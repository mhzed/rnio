package org.mhzed.nio;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.ScatteringByteChannel;

/**
 * Created by mhzed on 16-08-08.
 */
public class ReadChannel {
  private ScatteringByteChannel _impl;
  public ByteBuffer buffer;
  public static final int BufSize = 32*1024;     // 32k reasonable?

  public ReadChannel(ScatteringByteChannel c) {
    this._impl = c;
    buffer = ByteBuffer.allocate(BufSize);
  }

  // limit: ensures that no bytes larger than this amount is read from channel, -1 to not limit
  // returns: number of bytes actually read, -1 if eof reached
  public int read(int limit) throws IOException {
    buffer.clear();

    if (limit != -1 && limit < BufSize)
      buffer.limit(limit);
    else
      buffer.limit(BufSize);

    return this._impl.read(buffer);
  }
  public void newBuffer() {
    buffer = ByteBuffer.allocate(BufSize);
  }

}
