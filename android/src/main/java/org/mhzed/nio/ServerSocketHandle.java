package org.mhzed.nio;

import org.mhzed.NetModule;
import org.mhzed.Util;

import java.io.IOException;
import java.nio.channels.ClosedChannelException;
import java.nio.channels.SelectableChannel;
import java.nio.channels.SelectionKey;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;

/**
 * Created by mhzed on 16-08-08.
 */
public class ServerSocketHandle extends IoHandle {

  ServerSocketChannel c;

  public ServerSocketHandle(ServerSocketChannel c) throws IOException {

    c.configureBlocking(false);
    this.c = c;
    IoHandleFactory.get().allocId(this);
    NetDaemon.get().register(c.validOps(), this);
  }

  public SocketHandle accept() throws IOException {

    SocketChannel s = c.accept();
    if (s!=null) {
      s.configureBlocking(false);
      SocketHandle h = new SocketHandle(s);
      NetDaemon.get().register(0, h);   // by default, do not select on handle yet
      // emit event to JS
      NetModule.get().emitJsEvent(NetModule.EVT_ACCEPT_TCP, this.id(), h.id());
      return h;
    } else return null;
  }

  @Override
  public SelectableChannel selectableChannel() {
    return (SelectableChannel) this.c;
  }

  @Override
  public void close() throws IOException {
    IoHandleFactory.get().free(this.id());
    c.close();
  }
}

