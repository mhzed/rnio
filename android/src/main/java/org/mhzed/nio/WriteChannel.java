package org.mhzed.nio;

import org.mhzed.Util;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.GatheringByteChannel;
import java.util.ArrayDeque;
import java.util.ArrayList;

import com.facebook.react.bridge.Callback;
/**
 * Created by mhzed on 16-08-08.
 * For handeling buffer queue and callback invocation.  Call StreamHandle.write instead.
 */
public class WriteChannel {
  private GatheringByteChannel _impl;

  private ArrayDeque<ByteBuffer> _sendQueue;
  private ArrayDeque<Callback> _sendCb;
  private long nFlushed = 0;

  public WriteChannel(GatheringByteChannel c) {
    _sendQueue = new ArrayDeque<ByteBuffer>();
    _sendCb = new ArrayDeque<Callback>();
    this._impl = c;
  }

  public long nFlushed() {
    return this.nFlushed;
  }

  public boolean isQueueing() {
    synchronized (this._sendQueue) {
      return this._sendQueue.size()>0;
    }
  }

  private static class VoidCallback implements Callback {
    @Override
    public void invoke(Object... args) {
    }
  }
  private static VoidCallback _voidCb = new VoidCallback();
  // Only to be used by StreamHandle.write()
  // on return, if buff.hasRemaining() is true, then buff is queued internally, ensure:
  //    a. buff is not reused again
  //    b. continue to write after cb is invoked
  // if cb is not null, then cb(null,buff.limit()) is invoked when buff is flushed
  public boolean internalWrite(ByteBuffer buff, Callback cb) throws IOException {

    boolean flushed = false;
    synchronized (this._sendQueue) {
      if (this._sendQueue.size() > 0) {
        this._sendQueue.add(buff);
        this._sendCb.add(cb==null? _voidCb: cb);
      } else {
        nFlushed += this._impl.write(buff);
        if (buff.hasRemaining()) {    // need to store partially unwritten buffer
          this._sendQueue.add(buff);
          this._sendCb.add(cb==null? _voidCb: cb);
        } else flushed = true;
      }
    }
    if (flushed && cb != null) {
      cb.invoke(null, buff.limit()); // no queueing, invoke cb immediately
    }
    return flushed;
  }

  private ByteBuffer[] _p = new ByteBuffer[0];
  // for selector thread to continue writing
  // returns true if all buffer is flushed
  // Only to be used by NetDaemon thread
  public boolean internalWriteContinue() throws IOException {

    boolean flushed = false;
    ArrayList<Util.Tuple<Callback, ByteBuffer>> finishedCbs = null;
    synchronized (this._sendQueue) {
      if (this._sendQueue.size()>0) {

        ByteBuffer[] bufs = _sendQueue.toArray(_p);
        nFlushed += this._impl.write(bufs);

        finishedCbs = new ArrayList<Util.Tuple<Callback, ByteBuffer>>();
        for (ByteBuffer f : bufs) {
          if (!f.hasRemaining()) {
            ByteBuffer buff = this._sendQueue.removeFirst();
            Callback cb = this._sendCb.removeFirst();
            if (cb != _voidCb)
              finishedCbs.add(Util.tuple(cb, buff));
          }
        }
      }
      flushed = (this._sendQueue.size()==0);
    }
    if (finishedCbs != null) {
      for (Util.Tuple<Callback, ByteBuffer> t : finishedCbs) {
        t.one.invoke(null, t.two.limit());
      }
    }
    return flushed;
  }

}
