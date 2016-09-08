package org.mhzed.nio;

import java.io.IOException;
import java.nio.channels.SelectableChannel;

/**
 * Created by mhzed on 16-08-05.
 * Integer id is the bridge between java and js.
 */
public abstract class IoHandle {
  public IoHandle() {
  }

  public int id() {
      return id;
  }
  // this is called by IoHandleFactory only
  public void setId(int _id) {
    this.id = _id;
  }

  public abstract SelectableChannel selectableChannel();// return null if not selectable
  public abstract void close() throws IOException;       // must implement close

  private int id;
}
