/**
 * Created by mhzed on 2016-08-17.
 */
import {
  NativeModules,
  DeviceEventEmitter
} from 'react-native';
const b64 = require("./b64");
const {Net} = NativeModules;
const events = require('events')

class DgramSock  extends events {
  constructor(datacb) {
    super();
    if (datacb)
      this.on("message", datacb);
  }

  bind(option, cb) {
    let port=0, address;
    if (option) {
      let { _port, _address, exclusive } = option;
      address = _address || "0.0.0.0";
      port = _port;
    } else
      address = '';

    Net.createUdp(address, port, (err, fd)=>{
      if (err) {
        if (cb) cb(err);
        this.emit("error", err);
        return;
      }

      this._fd = fd;
      this.emit('listening');

      DeviceEventEmitter.addListener(Events.RECV_DATA(this._fd), (b64str)=>{
        let buff = b64.toByteArray(b64str);
        this.emit("message", buff);
      });
      if (cb) cb(err);
    });
  }

  close(cb) {
    Net.close(this._fd, (e)=>{
      DeviceEventEmitter.removeAllListeners(Events.RECV_DATA(this._fd));

      if (cb) cb(e);
      this.emit("close");
      this._fd = undefined;
    })
  }

  _ensureBind(cb) {
    if (this._fd === undefined) bind(null, cb);
    else cb();
  }

  // cb(err, nsent)
  send(chunk, port, address, cb) {
    _ensureBind((err)=>{
      if (err) {  // bind error
        if (cb) cb(err);
        return;
      }
      if (chunk instanceof Uint8Array) {
        chunk = b64.fromByteArray(chunk);
      } // else chunk is assumed to be in base64 already
      if (typeof chunk !== 'string') throw new Error("Bad payload " + (typeof chunk));

      Net.sendUdp(this._fd, address, port, chunk, (err, nsent)=>{
        if (err) this.emit("error", err);
        if (cb) cb(err, nsent);
      })
    })

  }
}

module.exports = DgramSock;