package org.mhzed.nio;

import java.io.IOException;
import java.nio.channels.FileChannel;
import java.nio.channels.SelectableChannel;

/**
 * Created by mhzed on 16-08-05.
 */
public class FileHandle extends StreamHandle {

  private FileChannel c;

  public FileHandle(FileChannel c) {
    this._reader = new ReadChannel(c);
    this._writer = new WriteChannel(c);
    this.c = c;
    IoHandleFactory.get().allocId(this);
  }

  public void seek(long position) throws IOException {
    this.c.position(position);
  }

  @Override
  public SelectableChannel selectableChannel() {
    return null;
  }

  @Override
  public void close() throws IOException {
    IoHandleFactory.get().free(this.id());
    this.c.close();
  }

}
