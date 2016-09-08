/**
 * Created by mhzed on 2016-07-20.
 */
const pmfy = require("pmfy")
const stream = require("stream");
const events = require('events');

const b64 = require("./b64");
const iopool = require("./iopool");
const UTF8 = require("utf8-encoder");
import {
  NativeModules
} from 'react-native';
const RNFS = NativeModules.RNFS;

/**
 * Never create this directly, use fs.createWriteStream instead
 */
class FsWriteStream extends stream.Writable {   // Event emitter

  constructor(options) {
    super(options);

    this._fd = options.fd;
    this._start = options.start;   // nodejs docs

    this._cur = this._start || 0;

    this.on('finish', ()=>{
      this._close();
    })
    iopool.add(this);
  }

  // convenience wrapper for writing string in utf8 encoding
  // on receive end, call UTF8.toString(bytes) to get back string
  writeutf8(str, cb) {
    super.write(UTF8.fromString(str), cb);
  }

  /**
   * when chunk is string, it MUST be b64 encoded data
   */
  _write(chunk, encoding, cb) {
    if (chunk instanceof Uint8Array) {
      chunk = b64.fromByteArray(chunk);
    }
    if (typeof chunk !== 'string') throw new Error("Bad payload " + (typeof chunk));

    RNFS.write(this._fd, chunk, 0, -1, this._cur, (err, length)=>{
      if (length) this._cur += length;
      cb(err, length);
    });
  }

  _close(cb) {
    iopool.del(this);
    if (this._fd !== undefined) {
      RNFS.close(this._fd, ()=> {
        if (cb) cb();
        this._fd = undefined;
      });

    }
  }
}


module.exports = FsWriteStream;