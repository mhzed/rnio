package org.mhzed.nio;

import org.mhzed.Util;

import java.io.IOException;
import java.nio.channels.CancelledKeyException;
import java.nio.channels.ClosedChannelException;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;

/**
 * Created by mhzed on 16-08-08.
 */
public class NetDaemon implements Runnable{

  static private Thread _t;
  static private NetDaemon _instance;

  private Selector selector;
  private boolean stopLoop = false;
  // both source/sink are blocking channels, therefore can be selected, store here
  private ArrayList<StreamPipe> pipes = new  ArrayList<StreamPipe>();

  private HashMap<IoHandle, Integer> asyncRegisters  = new HashMap<IoHandle, Integer>();

  private NetDaemon() throws IOException {
    selector = Selector.open();
  }

  static public void start() throws IOException {
    if (_t != null) return;   // alrady started

    _instance = new NetDaemon();
    _t = (new Thread(_instance));
    _t.start();
  }
  static public void stop() {
    _instance.stopLoop = true;
  }
  static public NetDaemon get() {
    return _instance;
  }

  // actual register happens in the select thread to avoid synchronization issue:
  // reigster call may block forever if selector thread doesn't sleep long enough in bewteeen select call
  private void _register(Integer opcodes, IoHandle h) throws ClosedChannelException {
    synchronized (this.asyncRegisters) {
      this.asyncRegisters.put(h, opcodes);
    }
  }
  private void _registerDelta(boolean add, Integer opcodes, IoHandle h) throws ClosedChannelException {
    synchronized (this.asyncRegisters) {
      // old op codes
      int oldopcodes = 0;
      SelectionKey oldkey = h.selectableChannel().keyFor(this.selector);
      if( oldkey!=null) oldopcodes |= oldkey.interestOps();
      Integer opc = this.asyncRegisters.get(h);
      if (opc != null) oldopcodes |=  opc;

      int newopcodes = add ? ( oldopcodes| opcodes) : (oldopcodes & ~opcodes);
      this.asyncRegisters.put(h, newopcodes);
    }
  }

  public void register(Integer opcodes, IoHandle h) throws ClosedChannelException {
    _register(opcodes, h);
    this.selector.wakeup();
  }
  public void registerDelta(boolean add, Integer opcodes, IoHandle h) throws ClosedChannelException {
    _registerDelta(add, opcodes, h);
    this.selector.wakeup();
  }

  // add non-blocking pipes, need to be started inside the selector thread to avoid synchronization issue.
  public void addPipes(StreamPipe p) {
    synchronized (this.pipes) {
      this.pipes.add(p);
    }
    this.selector.wakeup();
  }

  // call tick() in every loop, print out how many ticks every second
  class LoopCnt {
    private long then = System.currentTimeMillis();
    private int nloop = 0;
    private int nv = 0;
    void tick(int v) {
      nloop ++;
      nv += v;
      long now = System.currentTimeMillis();
      if (now - then >= 1000) {
        // log how busy the selector loop is
        Util.vlog("Net Daemon thread tick " + nloop  + " loops/s" + ", " + nv +"/s");
        then = now;
        nloop = 0;
        nv = 0;
      }
    }
  }

  private boolean _loopLog = false;
  public void loopLog(boolean on) {
    _loopLog = on;
  }
  public void run(){

    try {
      //Util.vlog("Net Daemon thread started");
      LoopCnt loopCnt = new LoopCnt();
      int nselected =0;
      while (!this.stopLoop) {
        if (_loopLog) loopCnt.tick(nselected);
        // order below are important, do not change adhoc.

        boolean piped = _advancePipes();
        _handleRegistrations(); // handle new channel registrations
        Set<SelectionKey> selectedKeys = null;
        try {
          // perform select
          // when there is blocking attachPipe, select should not block at all
          nselected = piped ?
              this.selector.selectNow() : this.selector.select(1000);

          if (nselected > 0) {
            selectedKeys = selector.selectedKeys();
          }
        } catch (Exception e) {
          Util.vlog("Ignoring unexpected select err: " + Util.strace(e));
        }

        if (selectedKeys!=null) { // handle selected channels
          Iterator<SelectionKey> iter = selectedKeys.iterator();
          while (iter.hasNext()) {
            SelectionKey key = iter.next();
            int ops = 0;
            try {
              ops = key.readyOps();
            } catch (CancelledKeyException e) {
              // expected, just ignore
              continue;
            }
            // ACCEPT, CONNECT, (READ | WRITE) are mutually exclusive
            if ( (ops & SelectionKey.OP_ACCEPT) !=0 )
              _handleAccept(key);
            else if ((ops & SelectionKey.OP_CONNECT) !=0)
              _handleConnect(key);
            else {
              if ((ops & SelectionKey.OP_READ) !=0) _handleRead(key);
              if ((ops & SelectionKey.OP_WRITE) !=0) _handleWrite(key);
            }
            iter.remove();
          } // end while
        } // end selectedKeys
      }
      this.selector.close();  // loop done
    } catch (Exception e) {
      Util.vlog("Netdaemon loop aborted due to unexpected error " + Util.strace(e));
    } finally {
    }
  }

  // advance pipes one step at a time: returns true if any of the pipe made any progress, false if no
  // progress is made;
  private boolean _advancePipes() {
    boolean advanced = false;
    try {
      if (this.pipes.size() >0) {
        synchronized (this.pipes) {
          Iterator<StreamPipe> iter = this.pipes.iterator();
          while (iter.hasNext()) {
            StreamPipe p = iter.next();
            if (p.next()) advanced = true;
            if (p.finished()) iter.remove();
          }
        }
      }
      return advanced;
    } catch (Exception e) {
      // Pipe.advance handles all exceptions, shouldn't get here, but just in case
      Util.vlog("Unexpected pipe error: " + Util.strace(e));
      return false;
    }
  }

  private void _handleRegistrations() {
    // handle pending channel register, must happen after select, for re-registration of channel:
    // select finishes cancel, then we can register
    if (this.asyncRegisters.size() > 0) {
      synchronized (this.asyncRegisters) {
        Iterator<Map.Entry<IoHandle,Integer>> iter = this.asyncRegisters.entrySet().iterator();
        while (iter.hasNext()) {

          Map.Entry<IoHandle,Integer> t = iter.next();
          int opCodes = t.getValue();
          IoHandle h = t.getKey();

          SelectionKey oldkey = h.selectableChannel().keyFor(this.selector);
          if (oldkey != null && oldkey.isValid()) {
            if (opCodes != oldkey.interestOps()) oldkey.interestOps(opCodes);
            iter.remove();
            //oldkey.cancel();
          } else {
            try {
              SelectionKey k = h.selectableChannel().register(this.selector, opCodes, h);
              iter.remove();  // registered ok, remove
            } catch (CancelledKeyException e) {
              // meaning that the channel's select op is being changed, keep it so that registration will
              // retry in next loop;
              Util.vlog("registering delayed" + h.id());
              _log(h.id(), "channel is being re-registered");
            } catch (ClosedChannelException e) {
              iter.remove();    // don't obbother log
            } catch (Exception e) { // this is entirely possible due to timing.. log for now anyways
              Util.vlog("Unexpected channel " + h.id() + " register error: " + Util.strace(e));
              iter.remove();  // remove
            }
          }
        }
      }
    }
  }
  private void _handleConnect(SelectionKey key) {
    SocketHandle h = (SocketHandle) key.attachment();
    _log(h.id(), "net daemon connecting ");
    try {
      if (h.finishConnect()) {
        _log(h.id(), "net daemon connected ");
        this._register(0, h);   // do not select anything yet
      }
      else ;    // leave it
    } catch (Exception e) {
      key.cancel();
      _log(h.id(), "net daemon connected failed " + Util.strace(e));
    }
  }
  private void _handleRead(SelectionKey key) {
    StreamHandle h = (StreamHandle) key.attachment();
    h.handleSelectedRead(key);
  }

  private void _handleWrite(SelectionKey key) {
    StreamHandle h = (StreamHandle) key.attachment();
    h.handleSelectedWrite(key);
  }

  private void _handleAccept(SelectionKey key) {
    ServerSocketHandle h = (ServerSocketHandle) key.attachment();
    try {
      _log( h.id(), "net daemon accepting ");
      SocketHandle newconn = h.accept();
      if (newconn != null)
        _log(h.id(), "net daemon accepted " + newconn.id() );
    } catch (IOException e) {
      _log(h.id(), "accept error " + Util.strace(e));
    }
  }

  // debug log function, for turnning on/off
  public static void _log(int id, String msg) {
    //Util.vlog(String.format("[%d]%s", id, msg));
  }

}
