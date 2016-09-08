package org.mhzed.nio;

import org.mhzed.NetModule;
import org.mhzed.Util;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.channels.SelectableChannel;
import java.nio.channels.SelectionKey;
import java.nio.channels.SocketChannel;
import com.facebook.react.bridge.Callback;
/**
 * Created by mhzed on 16-08-08.
 */
public class SocketHandle extends StreamHandle {

  private SocketChannel c;
  private Callback cb;

  public SocketHandle(SocketChannel c) throws IOException {
    c.configureBlocking(false);
    this._reader = new ReadChannel(c);
    this._writer = new WriteChannel(c);
    this.c = c;
    IoHandleFactory.get().allocId(this);

  }

  public void connect(String host, int port, Callback cb) throws IOException {
    InetSocketAddress remoteAddress = new InetSocketAddress(host, port);

    if (this.c.connect(remoteAddress)) {
      fireConnEvent("");  // connected immediately
      if (cb != null) cb.invoke();
    }
    else {
      this.cb = cb;
      NetDaemon.get().register(SelectionKey.OP_CONNECT, this);
    }
  }

  public boolean finishConnect() throws IOException {
    try {
      if (c.finishConnect()) {
        fireConnEvent("");
        if (this.cb!=null) this.cb.invoke();
        return true;
      }
      else
        return false;
    } catch (Exception e) {
      String etrace = Util.strace(e);
      fireConnEvent(etrace);
      if (this.cb!=null) this.cb.invoke(etrace);
      throw e;
    }
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

  private void fireConnEvent(String error) {
    // emit event to JS
    NetModule.get().emitJsEvent(NetModule.EVT_CONNECT_TCP, this.id(), error);
  }
}
