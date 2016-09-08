/**
 * Created by mhzed on 2016-07-20.
 */
import {
  NativeModules
} from 'react-native';
import { DeviceEventEmitter } from 'react-native';

const pmfy = require("pmfy")
const stream = require("stream");
const path = require('path');

const co = require("co");
const b64 = require("./b64");
const iopool = require("./iopool");
const _ = require("lodash");
const log = require("lawg");
const Events = require("./NetNativeEvents")
const UTF8 = require("utf8-encoder");
const pipeNative = pmfy(require("./pipeNative"));

/**
 *  To the extent possible, mirror nodejs fs api
 */
const {RNFS, Net} = NativeModules;

/**
 * Use net module to create/accept network connection.
 *
 * NetConn is closed by end() call, not closed by default!!
 *
 */
class NetConn extends stream.Duplex {


  constructor(fd) {
    super({objectMode: true});
    if (typeof fd == 'number') {
      this._attachFd(fd);
    }
    iopool.add(this);
  }

  // returns a promise
  pipeNative(dest, size, progressChunk, progressCb) {
    return pipeNative(this._fd, dest._fd, size, progressChunk, (nPiped)=>{
      this.emit("dataPiped", nPiped);
      if (progressCb) progressCb(nPiped);
    })
  }

  fdlog(msg){
    // if (this._fd !== undefined )
    //   log("["+this._fd+"]" + msg);
    // else
    //   log(msg);
  }

  _attachFd(fd) {
    if (this._fd!==undefined) throw new Error("already connected");
    this._fd = fd;

    DeviceEventEmitter.addListener(Events.RECV_DATA(this._fd),
      (b64str)=> {
        this.fdlog('native recv data');
        let buff = b64.toByteArray(b64str);
        this.push(buff);
      })
    DeviceEventEmitter.addListener(Events.RECV_ERR(this._fd),
      (err)=> {
        this.fdlog('native recv err ' + err);
        this.emit("error", err);
      })
    DeviceEventEmitter.addListener(Events.RECV_END(this._fd),
      ()=> {
        this.fdlog('native recv ending ');
        this.push(null);
      })
    DeviceEventEmitter.addListener(Events.SEND_ERR(this._fd),
      (err)=> {
        this.fdlog('native send err '  + err);
        this.emit("error", err);
      })

    this.on('finish', ()=>{
      this.fdlog('connection ' + ' closing');
      this._close();
    })
    this.on('error', (e)=>{ // in error we close by default
      this._close();
    })
  }

  connect(options, listenercb) {

    // pauseOnConnect: whether to pause data flow initially, default is false
    let {port, host, localAddress, localPort, pauseOnConnect} = options;

    if (!host) host = '127.0.0.1';
    if (!localAddress) localAddress = "0.0.0.0";
    if (!localPort) localPort = 0;

    if (typeof port === 'string') port = parseInt(port);
    if (typeof localPort === 'string') localPort = parseInt(localPort);

    Net.createTcp(localAddress, localPort, (err, fd)=>{
      if (err) return this.emit("error", err);
      this._attachFd(fd);
      Net.connectTcp(fd, host, port, (err)=>{
        if (err) {
          this._close();    // ensure handel is freed if connect failed
          this.emit("error", err);
        }
        else {
          this.emit("connect");
          if (listenercb) listenercb();
          this._pauseRead(!!pauseOnConnect);  // default: start data flow immediately
        }
      })
    })
  }

  // convenience wrapper for writing string in utf8 encoding
  // on receive end, call UTF8.toString(bytes) to get back string
  writeutf8(str, cb) {
    super.write(UTF8.fromString(str), cb);
  }

  // by default _close is called in 'finish' event, triggerd by calling end()...
  // for reading, after data is all consumed, call end() to close
  _close(cb){
    iopool.del(this);
    if (this._fd !== undefined) {
      DeviceEventEmitter.removeAllListeners(Events.RECV_DATA(this._fd));
      DeviceEventEmitter.removeAllListeners(Events.RECV_ERR(this._fd));
      DeviceEventEmitter.removeAllListeners(Events.RECV_END(this._fd));
      DeviceEventEmitter.removeAllListeners(Events.SEND_ERR(this._fd));

      this.emit("close");
      Net.close(this._fd, ()=> {
        if (cb) cb()
        this._fd = undefined;
      });
    }
  }

  /**
   * Pause read by not "select" on the socket handle in native implementation.  This is not the same
   * as nodejs stream pause/resume.  pipeNative needs this to control information flow.
   * @param on
   * @param cb
   * @private
   */
  _pauseRead(on, cb) {
    Net.pauseRead(this._fd, on, ()=>{ if (cb) cb()});
  }

  //@Override
  _read(size) {}

  //@Override
  _write(chunk, encoding, cb) {
    if (chunk instanceof Uint8Array) {
      chunk = b64.fromByteArray(chunk);
    } // else chunk is assumed to be in base64 already
    if (typeof chunk !== 'string') throw new Error("Bad payload " + (typeof chunk));
    Net.write(this._fd, chunk, cb ); // cb(err, bytelength)
  }

}


module.exports = NetConn;
