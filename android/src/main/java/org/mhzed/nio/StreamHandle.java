package org.mhzed.nio;

import android.util.Base64;

import com.facebook.react.bridge.Callback;

import org.mhzed.NetModule;
import org.mhzed.SpeedValve;
import org.mhzed.Util;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.ClosedChannelException;
import java.nio.channels.SelectableChannel;
import java.nio.channels.SelectionKey;

/**
 * IO sources that can process bytes in stream
 */
public abstract class StreamHandle extends IoHandle {

  protected ReadChannel _reader;
  protected WriteChannel _writer;

  public ReadChannel reader()   { return this._reader; }
  public WriteChannel writer()  { return this._writer; }

  public boolean write(ByteBuffer buff, Callback cb) throws IOException {
    boolean flushed = this._writer.internalWrite(buff, cb);
    if (!flushed) { // register for write event
      NetDaemon.get().registerDelta(true, SelectionKey.OP_WRITE, this);
    }
    return flushed;
  }

  private SpeedValve speedLimit;  // TODO: implement a read speed limit
  public void handleSelectedRead(SelectionKey key) {
    if (this._paused) return;

    NetDaemon._log(id(), "net daemon reading ");
    try {
      if (_readPipe != null) {
        NetDaemon._log(id(), "pipe via read");
        _readPipe.next();   //  _readPipe.next() call will set this._readPipe to NULL when pipe ends
      } else {
        int nread = _reader.read(-1);
        if (nread == -1) {
          NetDaemon.get().registerDelta(false, SelectionKey.OP_READ, this);
          NetModule.get().emitJsEvent(NetModule.EVT_RECV_END, id(), 0);
          NetDaemon._log(id(), "net daemon read end ");
        } else {
          if (nread > 0) { // yes there are lots of empty reads, selector is flaky
            String b64 = Base64.encodeToString(_reader.buffer.array(), 0, nread, Base64.NO_WRAP);
            NetModule.get().emitJsEvent(NetModule.EVT_RECV_DATA, id(), b64);
          }
          NetDaemon._log(id(), "net daemon read " + nread + " bytes");
        }
      }

    } catch (IOException e) {
      NetModule.get().emitJsEvent(NetModule.EVT_RECV_ERR, id(), Util.strace(e));
      // let JS handle error and close socket
    }

  }
  public void handleSelectedWrite(SelectionKey key) {
    NetDaemon._log(id(), "net daemon writing ");

    try {
      boolean flushed = _writer.internalWriteContinue();
      if (flushed) { // de-register OP_WRITE
        NetDaemon.get().registerDelta(false, SelectionKey.OP_WRITE, this);

        if (this._writePipe!=null) {  // also resume pipe's read from source if any
          this._writePipe.pauseRead(false);
        }
      }
      NetDaemon._log( id(), "net daemon written ");
    } catch (IOException e) {
      NetModule.get().emitJsEvent(NetModule.EVT_SEND_ERR, id(), Util.strace(e));
    }

  }

  private volatile boolean _paused = true;
  public void pauseRead(boolean on) throws ClosedChannelException {
    // no effect on unselectable channel
    if (this.selectableChannel() == null) return;

    this._paused = on;
    if (on) {
      NetDaemon.get().registerDelta(false, SelectionKey.OP_READ, this);
    } else {
      NetDaemon.get().registerDelta(true, SelectionKey.OP_READ, this);
    }
  }

  // attachPipe from this to to end for "size" number of bytes, or until end if "size" is -1
  // if progressChunk > 0, fire pipe progress event to JS every this chunk of bytes piped
  public void pipe(final StreamHandle sink, final long size, final int progressChunk, final Callback cb) throws Exception {
    final StreamPipe p = new StreamPipe(this, sink, size, progressChunk, cb);
    //Util.vlog("piping " + (isSourceBlocking?"blocking":"nonblocking") + " to " + (isSinkBlocking?"blocking":"nonblocking"));
    sink.pauseRead(false);
    NetDaemon.get().addPipes(p);
  }
  public PipeInt _readPipe = null, _writePipe = null;

  // helper: test if channel can be selected (or non-blocking)
  public static<T> boolean isBlockingChannel(T channel) {
    try {
      if (channel == null) return false;
      SelectableChannel s = (SelectableChannel)channel;
      return s.isBlocking();
    } catch (ClassCastException e) {
      return true;
    }
  }
}
