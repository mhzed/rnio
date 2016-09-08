/**
 * Created by mhzed on 2016-07-20.
 */
import {
  NativeModules,
  DeviceEventEmitter
} from 'react-native';

const NetConn = require("./NetConn");

const pmfy = require("pmfy")
const stream = require("stream");
const path = require('path');
const events = require('events');

const co = require("co");
const b64 = require("./b64");
const iopool = require("./iopool");
const _ = require("lodash");
const Events = require("./NetNativeEvents");
const {RNFS, Net} = NativeModules;

const DefaultOpts = {
  allowHalfOpen: false,
  pauseOnConnect: false
}

/**
 * Don't new this, use net module
 */
class NetServer extends events {

  constructor(opts, connectionListenerCb) {
    super();
    if (typeof opts === 'function') {
      connectionListenerCb = opts;
      opts = {};
    }
    this.opts = _.assign({}, DefaultOpts, opts || {});
    this.connectionListenerCb = connectionListenerCb;
    iopool.add(this);
  }

  listen(port, host, cb) {
    if (this._fd!==undefined) throw new Error("already listening");
    if (typeof host === 'function') {
      cb = host;
      host = "0.0.0.0";
    } else if (!host) {
      host = "0.0.0.0";
    }

    let {pauseOnConnect} = this.opts;
    Net.createTcpServer(host, port, (err, fd)=>{
      if (!err) {
        this._fd = fd;
        this.emit("listening");
        DeviceEventEmitter.addListener(
          Events.ACCEPT_TCP(fd),
          (sockFd)=>{
            let conn = new NetConn(sockFd);
            if (this.connectionListenerCb)
              this.connectionListenerCb(conn);
            this.emit("connection", conn);
            conn._pauseRead(pauseOnConnect);
          }
        )
      } else this.emit("error", err);
      if (cb) cb(err);
    })
  }

  close(cb) {
    iopool.del(this);
    if (this._fd !== undefined) {
      DeviceEventEmitter.removeAllListeners(Events.ACCEPT_TCP(this._fd));
      Net.close(this._fd, ()=> {
        cb();
        this.emit("close");
        this._fd = undefined;
      });
    }
  }

}


module.exports = NetServer;
