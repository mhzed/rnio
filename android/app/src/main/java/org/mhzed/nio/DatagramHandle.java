package org.mhzed.nio;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.DatagramChannel;
import java.nio.channels.SelectableChannel;
import java.nio.channels.SelectionKey;

import com.facebook.react.bridge.Callback;
/**
 * Created by mhzed on 16-08-08.
 */
public class DatagramHandle extends StreamHandle {
  private DatagramChannel c;
  public DatagramHandle(DatagramChannel c) throws IOException {
    this.c = c;
    c.configureBlocking(false);
    this._reader = new ReadChannel(c);

    IoHandleFactory.get().allocId(this);
    NetDaemon.get().register(SelectionKey.OP_READ, this);
  }

  @Override
  public SelectableChannel selectableChannel() {
    return (SelectableChannel)this.c;
  }

  @Override
  public void close() throws IOException {
    IoHandleFactory.get().free(this.id());
    this.c.close();
  }

  // see DatagramChannel.send()
  public int send(ByteBuffer buffer, InetSocketAddress remoteAddr) throws IOException {
    return this.c.send(buffer, remoteAddr);
  }


}
