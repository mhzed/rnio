package org.mhzed.nio;

import com.facebook.react.bridge.Callback;

import org.mhzed.NetModule;
import org.mhzed.SpeedValve;
import org.mhzed.Util;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.ClosedChannelException;

/**
 *  if next() returns true, pipe progress has been made: meaning 'something' was read:  bytes or eof or error
 *  if next() returns false, the no progress is made: 0 byte read or write is queuing or pipe is done
 *  if finished() returns true then all reads are definitely done, but not all writes necessarily
 *  if cb in constructor is invoked, then all writes are also done, pipe is truely finished.
 *  if eof() tests whether pipe finished due to source eof-ed.
 *
 *  Not thread safe, no matter since all methods are called in NetDaemon thread.
 *
 *  if sink is not as fast as source, then _soure.pauseRead(true/false) is called.  pauseRead works
 *  by register/de-regsiter OP_READ on source selectablChannel.
 *
 */

class StreamPipe implements PipeInt {
  private StreamHandle _source;      // from _source.reader()
  private StreamHandle _sink;       // to   _sink.writer()
  private long size, remaining;
  private boolean eof = false;
  private int progressIncrement = 0;

  private Callback cb ;       // cb to invoke when pipe is completely done: all writes are flushed
  public StreamPipe(StreamHandle source, StreamHandle sink, long size, int progressIncrement, Callback cb) {
    this._source = source;
    this._sink = sink;
    this._source._readPipe = this;
    this._sink._writePipe = this;

    this.size = size;
    this.remaining = size;
    this.cb = cb;

    this.progressIncrement = progressIncrement;
  }

  public long bytesPiped() {
    if (size>=0) return size - remaining;
    else return (-remaining) - 1;   // remaining start at -1
  }

  // NetDaemon thread needs to call this unpause, thus public
  @Override
  public void pauseRead(boolean on) throws ClosedChannelException {
    this._source.pauseRead(on);
  }
  // finished meaning no more progress is possible: pipe is finished or error-ed.  But callback
  // may or may not be invoked yet : sink write may be pending
  @Override
  public boolean finished() {
    return cb == null;
  }

  @Override
  public boolean eof() {
    return eof;
  }

  private long lastReportN = 0;
  private void reportProgress() {
    long n = bytesPiped();
    if (progressIncrement > 0 && (n - lastReportN) > progressIncrement) {
      NetModule.get().emitJsEvent(NetModule.EVT_PIPE_BYTES, this._source.id(), n);
      lastReportN = n;
    }
  }
  // returns true: progress has been made
  // returns false: no progress has been made
  public boolean next()  {

    if (this.cb == null) {    // no more progress possible
      return false;
    }
    try {
      if (_sink.writer().isQueueing()) {
        return false;   // no progress made, wait until writer is flushed
      }

      if (remaining == 0) {          // no more to pipe, mark end, progress is bade
        _markSinkEnd(null);
      } else {

        int chunkSize = ReadChannel.BufSize;
        if (remaining > 0 && chunkSize > remaining) chunkSize = (int) remaining;  // apply limit
        int nread = _source.reader().read(chunkSize);
        if (nread == -1) {  // source eof reached, no more to attachPipe
          this.eof = true;
          _markSinkEnd(null);
        } else {
          if (nread > 0) {
            remaining -= nread;
            reportProgress();

            _source.reader().buffer.flip();
            boolean queued = !_sink.write(_source.reader().buffer, null);
            if (queued) {
              _source.reader().newBuffer();
              pauseRead(true);// see StreamHandle.handleSelectedWrite for resume read
            }
          } else return false;    // read 0 byte, no progress
        }
      }
    } catch (Exception e) {
      _markSinkEnd(e);
    } finally {
      return true;
    }
  }


  ByteBuffer zeroBuffer = ByteBuffer.allocate(0);
  class EndCb implements Callback {
    private Callback pipeEndCb;
    private boolean eof;
    private long bytesPiped;
    EndCb(Callback pipeEndCb, boolean eof, long bytesPiped) {
      this.pipeEndCb = pipeEndCb;
      this.eof = eof;
      this.bytesPiped = bytesPiped;
    }

    @Override
    public void invoke(Object... args) {
      pipeEndCb.invoke(null, eof, (double)bytesPiped);
    }
  }
  private void _markSinkEnd(Exception e) throws IOException {
    pauseRead(true);
    if (e!=null) {
      this.cb.invoke(Util.strace(e));
      this.cb = null;
    } else {
      if (progressIncrement > 0)
        NetModule.get().emitJsEvent(NetModule.EVT_PIPE_BYTES, this._source.id(), bytesPiped());

      if (!StreamHandle.isBlockingChannel(this._sink)) {
        EndCb endcb = new EndCb(this.cb, this.eof, this.bytesPiped());
        this.cb = null;
        this._sink.write(zeroBuffer, endcb);
      } else {
        this.cb.invoke(null, this.eof, (double) this.bytesPiped());
        this.cb = null;
      }
    }

    this._source._readPipe = null;
    this._sink._writePipe = null;
  }
};
