package org.mhzed;

import android.provider.Settings;
import android.util.Base64;

import com.facebook.react.bridge.ReactApplicationContext;
import com.facebook.react.bridge.ReactContextBaseJavaModule;
import com.facebook.react.bridge.ReactMethod;
import com.facebook.react.bridge.Callback;
import com.facebook.react.modules.core.DeviceEventManagerModule;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.DatagramChannel;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;

import org.mhzed.nio.StreamHandle;
import org.mhzed.nio.DatagramHandle;
import org.mhzed.nio.IoHandleFactory;
import org.mhzed.nio.NetDaemon;
import org.mhzed.nio.ServerSocketHandle;
import org.mhzed.nio.SocketHandle;

/**
 * enable tcp/udp socket in JS.
 */
public class NetModule extends ReactContextBaseJavaModule {

  public static final String EVT_ACCEPT_TCP = "org.mhzed.nio.NetModule.accept.";
  public static final String EVT_CONNECT_TCP = "org.mhzed.nio.NetModule.connect.";
  public static final String EVT_RECV_DATA = "org.mhzed.nio.NetModule.recvdata.";
  public static final String EVT_RECV_ERR = "org.mhzed.nio.NetModule.recverr.";
  public static final String EVT_RECV_END = "org.mhzed.nio.NetModule.recvend.";
  public static final String EVT_SEND_ERR = "org.mhzed.nio.NetModule.senderr.";
  public static final String EVT_PIPE_BYTES = "org.mhzed.nio.NetModule.pipebytes.";

  private ReactApplicationContext reactContext;

  public NetModule(ReactApplicationContext reactContext) {
    super(reactContext);
    this.reactContext = reactContext;
    try {
      NetDaemon.start();
    } catch (IOException e) {
      e.printStackTrace();
    }
    _instance = this;
  }

  // NetModule is not a singleton, but tis life cycle is handled by react native,  module may be
  // recreated upon app reload (but JVM process remains).
  private static NetModule _instance = null;

  public static NetModule get() {
    return _instance;
  }

  private void _check() throws Exception {
    if (NetDaemon.get() == null) throw new Exception("Net daemon thread is not started");
  }

  public void emitJsEvent(String eventName, int sourceId, int id) {
    //Util.vlog("emitting " +  eventName  + sourceId + ": " + id) ;
    reactContext
        .getJSModule(DeviceEventManagerModule.RCTDeviceEventEmitter.class)
        .emit(eventName + sourceId, id);
  }

  public void emitJsEvent(String eventName, int sourceId, double id) {
    //Util.vlog("emitting " +  eventName  + sourceId + ": " + id) ;
    reactContext
        .getJSModule(DeviceEventManagerModule.RCTDeviceEventEmitter.class)
        .emit(eventName + sourceId, id);
  }

  public void emitJsEvent(String eventName, int sourceId, String data) {
    //Util.vlog("emitting " + eventName  + sourceId + ": " + data);
    reactContext
        .getJSModule(DeviceEventManagerModule.RCTDeviceEventEmitter.class)
        .emit(eventName + sourceId, data);
  }

  @Override
  public String getName() {
    return "Net";
  }

  @ReactMethod
  public void createTcpServer(String localhost, int localport, Callback cb) {
    try {
      _check();
      ServerSocketChannel serverSocket = ServerSocketChannel.open();
      InetSocketAddress hostAddress = new InetSocketAddress(localhost, localport);
      serverSocket.socket().bind(hostAddress);

      ServerSocketHandle h = new ServerSocketHandle(serverSocket);
      cb.invoke(null, h.id());
    } catch (Exception e) {
      cb.invoke(Util.strace(e));
    }
  }


  @ReactMethod
  public void createTcp(String localhost, int localport, Callback cb) {
    try {
      _check();
      InetSocketAddress localAddress = new InetSocketAddress(localhost, localport);
      SocketChannel client = SocketChannel.open();
      client.socket().bind(localAddress);
      SocketHandle h = new SocketHandle(client);

      cb.invoke(null, h.id());
    } catch (Exception e) {
      cb.invoke(Util.strace(e));
    }
  }

  // initiate connect, when connection is established/finished, two things happen:
  // 1. cb is invoked
  // 2. connection event is fired to JS envrionment
  @ReactMethod
  public void connectTcp(Integer id, String host, int port, Callback cb) {
    try {
      _check();
      SocketHandle h = (SocketHandle) IoHandleFactory.get().find(id);
      h.connect(host, port, cb);
    } catch (Exception e) {
      cb.invoke(Util.strace(e));
    }
  }

  /**
   * pipe in Java code, for performance.
   * sourceFd is automatically placed in 'paused' mode after pipe finishs.  This is to prevent 'data' from
   * sourceFd being lost while re-connecting pipes or data handler.  Call pauseRead(fd, false) to resume reading.
   * calling 'pipe' will also auto-unpause sourceFd if it's already paused.
   *
   * @param sourceFd    pipe from
   * @param targetFd    pipe dest
   * @param size        how many bytes to pipe
   * @param progressChunkSize fire progress events (for sourceFd) when every time at least this many bytes transferred
   * @param cb                (err, eof, bytesPiped) : eof is true if sourceFd's end of file is reached
   */
  @ReactMethod
  public void pipe(Integer sourceFd, Integer targetFd, Double size, Integer progressChunkSize, Callback cb) {
    try {
      StreamHandle source = (StreamHandle) IoHandleFactory.get().find(sourceFd);
      StreamHandle target = (StreamHandle) IoHandleFactory.get().find(targetFd);
      source.pipe(target, Math.round(size), progressChunkSize, cb);
    } catch (Exception e) {
      cb.invoke(Util.strace(e));
    }
  }

  /**
   * For test, pause/resume reading on network connection. If file fd is passed in, error in cb.
   */
  @ReactMethod
  public void pauseRead(Integer sourceFd, Boolean on, Callback cb) {
    try {
      StreamHandle source = (StreamHandle) IoHandleFactory.get().find(sourceFd);
      source.pauseRead(on);
      cb.invoke();
    } catch (Exception e) {
      cb.invoke(Util.strace(e));
    }
  }


  @ReactMethod
  public void createUdp(String localhost, int localport, Callback cb) {
    try {
      _check();
      DatagramChannel client = DatagramChannel.open();
      if (localhost.length() > 0) {
        InetSocketAddress localAddr = new InetSocketAddress(localhost, localport);
        client.socket().bind(localAddr);
      }
      DatagramHandle h = new DatagramHandle(client);
      cb.invoke(null, h.id());
    } catch (Exception e) {
      cb.invoke(Util.strace(e));
    }
  }

  // udp send, no internal buffering is performed
  // cb(err, nSent) : nSent is either 0 or length of buffer in bytes, 0 means nothing is sent or
  // ever will be sent.  caller needs to handle it.
  @ReactMethod
  public void sendUdp(Integer fd, String remotehost, int remoteport, String b64, Callback cb) {
    try {
      DatagramHandle h = (DatagramHandle) IoHandleFactory.get().find(fd);
      InetSocketAddress remoteAddr = new InetSocketAddress(remotehost, remoteport);
      byte[] buff = Base64.decode(b64, Base64.NO_WRAP);
      int sent = h.send(ByteBuffer.wrap(buff, 0, buff.length), remoteAddr);
      cb.invoke(null, sent);
    } catch (Exception e) {
      cb.invoke(Util.strace(e));
    }

  }

  @ReactMethod
  public void createSslServer(String host, int port, Callback cb) {
    try {
      _check();
      cb.invoke();
    } catch (Exception e) {
      cb.invoke(Util.strace(e));
    }
  }

  @ReactMethod
  public void createSsl(String host, int port, Callback cb) {
    try {
      _check();
      cb.invoke();
    } catch (Exception e) {
      cb.invoke(Util.strace(e));
    }
  }


  /**
   * write buffer to socket.  callback is invoked after the entire buffer is written to
   * underlying communication channel or if error occurred.
   *
   * @param id  socket handle
   * @param b64 buffer in b64 encoded format
   * @param cb  (err, length) length of buffer (after conversion from b64) in bytes
   */
  @ReactMethod
  public void write(Integer id, String b64, Callback cb) {
    try {
      _check();
      StreamHandle h = (StreamHandle) IoHandleFactory.get().find(id);
      byte[] buff = Base64.decode(b64, Base64.NO_WRAP);
      ByteBuffer buffer = ByteBuffer.wrap(buff, 0, buff.length);
      h.write(buffer, cb); // cb is invoked by write()
    } catch (Exception e) {
      String etrace = Util.strace(e);
      NetModule.get().emitJsEvent(NetModule.EVT_SEND_ERR, id, etrace);
      cb.invoke(etrace);
    }
  }

  // close same handle twice will crash:  this is intentional as id gets reused, JS must ensure
  // close is called once
  @ReactMethod
  public void close(Integer id, Callback cb) {
    try {
      _check();
      IoHandleFactory.get().find(id).close();
      cb.invoke();
    } catch (Exception e) {
      cb.invoke(Util.strace(e));
    }
  }


  // for debug only
  @ReactMethod
  public void selectLoopLog(boolean on, Callback cb) {
    NetDaemon.get().loopLog(on);
    cb.invoke();
  }
}