/**
 * Created by mhzed on 2016-07-20.
 */
const pmfy = require("pmfy")
const stream = require("stream");
const events = require('events');

const b64 = require("./b64");
const iopool = require("./iopool");
import {
  NativeModules
} from 'react-native';
const {RNFS, Net} = NativeModules;
import { DeviceEventEmitter } from 'react-native';
const pipeNative = pmfy(require("./pipeNative"));

/**
 * Never create this directly, use fs.createReadStream instead
 * emitted 'data' event's paramter is always Uint8Array
 */
class FsReadStream extends stream.Readable {   // Event emitter

  constructor(options) {
    super(options);

    this._fd = options.fd;
    this._start = options.start;   // nodejs doc, [start, end] are inclusive
    this._end = options.end;

    this._chunkSize = options.chunkSize || 1024*128;
    this._cur = this._start || 0;

    this.on('end', ()=>{    // auto close
      this._close();
    })
    iopool.add(this);
  }

  // returns a promise: [eof, nPiped]
  // progressChunk, progressCb are optional
  pipeNative(dest, size, progressChunk, progressCb) {
    return pipeNative(this._fd, dest._fd, size, progressChunk, progressCb);
  }

  _read() {
    let size = this._chunkSize;
    let toReadSize = size;
    let checkEnd = (this._end !== undefined && this._end !== null)
    if (checkEnd) toReadSize = Math.min(size, this._end - this._cur + 1)

    RNFS.read(this._fd, toReadSize, this._cur , (err, b64str, n)=>{
      if (err) this.emit('error', err);
      else {
        if (n == -1) this.push(null);
        else {
          this._cur += n;
          this.push(b64.toByteArray(b64str))
          if (checkEnd && this._cur >= this._end) this.push(null);
        }
      }
    })
  }


  _close(cb){
    iopool.del(this);
    if (this._fd !== undefined) {
      RNFS.close(this._fd, ()=> {
        if (cb) cb();
        this._fd = undefined;
      });
    }
  }

}


module.exports = FsReadStream;