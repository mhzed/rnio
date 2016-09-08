/**
 * Created by mhzed on 2016-07-20.
 */
import {
  NativeModules
} from 'react-native';
const pmfy = require("pmfy")
const path = require('path')
const co = require("co");
const b64 = require("./b64");
const _ = require("lodash");
const FsWriteStream = require("./FsWriteStream")
const FsReadStream = require("./FsReadStream")
const UTF8 = require("utf8-encoder");
/**
 *  To the extent possible, mirror nodejs fs api
 */
const RNFS = NativeModules.RNFS;

const mtimeFile=(fpath)=>{
  return path.resolve(path.dirname(fpath), '.' + path.basename(fpath) + '.mtime');
}

const fs = {

  readdir : (fpath, cb)=>{
    "use strict";
    RNFS.readdir(fpath, cb);
  },

  stat : (fpath, cb)=>{
    "use strict";
    if (!fpath) return cb(new Error("undefined parameter"));
    RNFS.stat(fpath, (err, _s)=>{
      let s;
      if (_s) {
        s = {};
        s.mtime = new Date(parseInt(_s.mtime));
        s.size = parseInt(_s.size);
        s._t = _s._t;
        s.isFile = function() {return this._t == 1}
        s.isDirectory = function() {return this._t == 0};
      }
      cb(err, s);
    })
  },

  // cb(err, fd):  fd is a number representing a file descriptor
  // flags: is ignored, file is always open in read/write random access mode
  open : (fpath, flags, cb) =>{
    "use strict";
    RNFS.open(fpath, flags, cb);
  },

  // close opened fd
  close : (fd, cb)=>{
    "use strict";
    RNFS.close(fd, cb);
  },

  unlink : (fpath, cb)=>{
    "use strict";
    RNFS.unlink(fpath, cb);
  },
  rename : (old, newpath, cb) =>{
    "use strict";
    RNFS.rename(old, newpath, cb);
  },
  utimes : (fpath, atime, mtime, cb) =>{
    "use strict";
    // android fs' mtime/atime can not be changed
    if (cb) cb(new Error("Not supported"));
  },
  mkdir : (fpath, cb)=>{
    "use strict";
    RNFS.mkdir(fpath, cb);
  },

  rmdir : (fpath, cb)=>{
    "use strict";
    fs.stat(fpath, (err, s)=>{
      if (err) return cb(err);
      if (s.isDirectory())
        RNFS.unlink(fpath, cb);
      else
        cb(new Error(`${fpath} is not a directory`));
    })

  },

  // reads utf8 encoded text
  readFile : (fpath, cb)=>{
    "use strict";
    fs.open(fpath, "", (err, fd)=>{
      if (err) return cb(err);
      let rs = fs.createReadStream({fd});
      let content = "";
      rs.on("data", (chunk)=>{
        content += UTF8.toString(chunk);
      })
      rs.on("end", ()=>{
        cb(null, content);
      })
    })
  },
  // writes text utf8 encoded
  writeFile : (fpath, str, cb)=>{
    "use strict";
    fs.open(fpath, "", (err, fd)=>{
      if (err) return cb(err);
      let ws = fs.createWriteStream({fd});
      let buff = b64.fromByteArray(UTF8.fromString(str));
      ws.end(buff, cb);
    })
  },
  /**
   * createFsReadStream/createWriteStream:
   * - file must be opened first, to obtain fd
   * - always auto closes
   * - streams are always in objectMode to disable default Buffering behavior
   * - no encoding supported, Uint8Array binary data only (do NOT use nodejs Buffer),
   * - string payload is assumed to be b64 encoded binary data
   *
   * @param opts  { fd, start, end }
   * @returns {*|any} a readable stream
   */
  createReadStream : (opts)=>{
    "use strict";
    if (typeof opts !== 'object') throw new Error("Open file first");
    opts.objectMode = true;     // critical, disable funny Buffer handling in stream implementation
    return new FsReadStream(opts);
  },
  /**
   *
   * @param opts { fd, start }
   * @returns {any|*}
   */
  createWriteStream : (opts)=>{
    "use strict";
    if (typeof opts !== 'object') throw new Error("Open file first");
    opts.objectMode = true;
    return new FsWriteStream(opts);
  },
  ///////////////////////////////////////////////////////////
  ///////// non-nodejs related apis
  readBuff : (fd, length, position, cb)=>{
    "use strict";
    RNFS.read(fd, length, position, (err, _b64buff, sizeRead)=>{
      let buff;
      if (_b64buff) buff = b64.toByteArray(_b64buff)
      cb(err, buff, sizeRead);
    });
  },
  writeBuff : (fd, buff, position, cb)=>{
    "use strict";
    RNFS.write(fd, b64.fromByteArray(buff), 0, buff.length, position, cb);
  },
  // this is for benchmark
  copy : (fromfd, tofd, size, cb)=>{
    "use strict";
    RNFS.copy(fromfd, tofd, size, cb);
  },


  handleAllocSize : (cb) => {
    "use strict";
    RNFS.handleAllocSize(cb);
  }

}

// a version of fs that's only promise
fs.p = {

  readdir : pmfy(fs.readdir),

  stat : pmfy(fs.stat),

  // cb(err, fd):  fd is a number representing a file descriptor
  // flags: is ignored, file is always open in read/write random access mode
  open : pmfy(fs.open),

  close : pmfy(fs.close),

  unlink : pmfy(fs.unlink),
  rmdir : pmfy(fs.rmdir),
  mkdir : pmfy(fs.mkdir),
  rename : pmfy(fs.rename),
  readFile : pmfy(fs.readFile),
  writeFile : pmfy(fs.writeFile),

  readBuff : pmfy(fs.readBuff),

  writeBuff : pmfy(fs.writeBuff),

  copy : pmfy(fs.copy),
  handleAllocSize : pmfy(fs.handleAllocSize),

  // set stat.mtime by reading from a hidden metadata file if exists
  // Returned Promise resolves to true if stat.mtime is set, false otherwise
  fillMetaMtime : (fpath, stat) =>{
    "use strict";
    return co(function*() {
      try {
        let mtimefile = mtimeFile(fpath);
        if (yield fs.p.exists(mtimefile)) {
          let [ftime, mtime] = (yield fs.p.readFile(mtimefile)).split(',');
          ftime = parseInt(ftime);
          mtime = parseInt(mtime);
          if (stat.mtime.getTime() == ftime)
            stat.mtime = new Date(mtime);
          return true;
        }
      } catch (e) { // suppress error intentionally
        console.log(e);
      }
      return false;
    });
  },
  // metaTime should be int (ms since 1970)
  // metaTime is the override meta modify time as set by bytepipe sink
  setMetaMtime : (fpath, metaTime)=>{
    "use strict";
    return co(function*() {
      let filetime = (yield fs.p.stat(fpath)).mtime.getTime();
      let mtimefile = mtimeFile(fpath);
      if (typeof metaTime != 'number') metaTime = metaTime.getTime();
      yield fs.p.writeFile(mtimefile, filetime + ',' + metaTime);
    });
  },
  delMetaMtime : (fpath) =>{
    "use strict";
    return co(function*() {
      let mtimefile = mtimeFile(fpath);
      yield fs.p.unlink(mtimefile);
    });
  },


  exists : (path)=>{
    "use strict";
    return new Promise((res, rej)=>{
      fs.stat(path, (err)=>{
        if (err) res(false);
        else res(true);
      })
    })
  },
  ensureDir : (fpath)=>{
    "use strict";
    return co(function*(){
      let stat;
      try {
        stat = yield fs.p.stat(fpath);
        if (stat.isDirectory()) return; // already a dir
      } catch (e) {
        if (/does not exist/.test(e)) { // a bit of hack, but.......
          yield fs.p.ensureDir(path.dirname(fpath));
          yield fs.p.mkdir(fpath);
          return;
        } else throw e;
      }
      // not a dir
      throw new Error(`${fpath} is file`);
    })
  },
  emptyDir : (fpath)=>{
    "use strict";
    return co(function*(){
      for (let f of yield fs.p.readdir(fpath)) {
        let fullpath = path.resolve(fpath, f);
        let stat = yield fs.p.stat(fullpath);
        if (stat.isDirectory()) {
          yield fs.p.emptyDir(fullpath);
          yield fs.p.rmdir(fullpath);
        }
        else
          yield fs.p.unlink(fullpath);
      }
    })
  },
  ensureFile : (fpath)=>{
    "use strict";
    return co(function*(){
      yield fs.p.ensureDir(path.dirname(fpath));
      let ws = fs.createWriteStream({
        fd: yield fs.p.open(fpath, "")
      });
      yield pmfy(ws.end, ws)();
    })
  },
  // returns a promise
  // filter(fullpath, rpath, stat): returns the object to save, or a promise that resolves to the object to save.
  // recurseCb(fullapth, rpath, stat): returns true to walk into dir, false otherwise
  walkdir : (fpath, filter, recurseCb) =>{
    "use strict";
    const fnReadDir = fs.p.readdir;
    const fsStat = fs.p.stat;
    // add root for calculating relative path during recursion
    const _readdirrec = (fpath, root, filter, recurseCb)=>{
      return fnReadDir(fpath).then((files)=>{
        files.sort();
        return co(function*(){   // allow calling promises synchronously ,itself is a promise
          let fstats = [];
          for (let f of files) {
            let fullpath = path.resolve(fpath, f);
            let stat = yield fsStat(fullpath);
            let vfile;
            let rpath = path.relative(root, fullpath);
            if (stat.isFile() || stat.isDirectory()) {
              if (!filter) vfile = rpath;
              else {
                let ret = filter(fullpath, rpath, stat);
                if (ret && typeof ret.then === 'function') vfile = yield ret;
                else vfile = ret;
              }
              if (vfile) fstats.push(vfile);
            }
            if (stat.isDirectory()) {
              let dorec;
              if (!recurseCb) dorec = true;
              else {
                let ret = recurseCb(fullpath, rpath, stat);
                if (ret && typeof ret.then === 'function') dorec = yield ret;
                else dorec = ret;
              }
              if (dorec)
                fstats.push.apply(fstats, yield _readdirrec(fullpath, root, filter));  // append
            }
          }
          return fstats;
        })
      });
    }
    return _readdirrec(fpath, fpath, filter, recurseCb);
  },


}
module.exports = fs;