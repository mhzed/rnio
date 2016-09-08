package org.mhzed.nio;

import java.nio.channels.ClosedChannelException;

/**
 * Created by mhzed on 16-08-09.
 */
public interface PipeInt {
  // advance must:
  // 1. return true to indicate attachPipe is in progress: advance-able
  // 2. return false to indicate attachPipe is done (finished or erred). Done meaning all buffer
  //    from source is written to sink, but it's not guarantted that sink has flushed all buffer.
  // 3. handle/suppress any exceptions (return false instead)
  public boolean next();
  public boolean finished();
  public boolean eof();
  public long bytesPiped();

  public void pauseRead(boolean on) throws ClosedChannelException;
}
