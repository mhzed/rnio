/**
 * Tests the native modules
 */
import {
  NativeModules,
  DeviceEventEmitter
} from 'react-native';
const {Mess, Sys, Net} = NativeModules;

const path = require("path");
const assert = require('assert');
const fs = require("../lib/fs");
const net = require("../lib/net");
const _ = require("lodash");
const co = require("co");
const pmfy = require("pmfy");
const future = pmfy(require("phuture"));
const UTF8 = require("utf8-encoder");
const log = require("lawg");

let TestDir, TestTextFile, TestBinFile;
const outFileName = (fname, sig) =>{
  "use strict";
  let ext = path.extname(fname);
  return path.resolve(path.dirname(fname), path.basename(fname,ext) + sig + ext);
}


tests = {
  'begin' : (test) =>{
    "use strict";
    Net.selectLoopLog(true, test.done);
  },
  'test cb in thread' : (test)=>{
    "use strict";
    let i=0, j=0;
    Mess.callbackInThread(()=>{
      // if doesn't work, test should be stuck
      test.done();
    });
  },
  "double echo" : (test)=>{
    "use strict";
    let n = Number.MAX_SAFE_INTEGER -1;
    // echoDouble takes double in Java, convert to Long via 'round', then return string representation of Long value
    Mess.echoDouble(n).then((_n)=>{
      "use strict";
      test.equal(""+n, _n, "no precision should be lost");
      test.done();
    })
  },
  "test utf8" : (test)=>{
    "use strict";
    let str = `
   北京时间8月11日消息，2016年里约奥运会乒乓球项目的首枚金牌产生，丁宁如愿以偿夺冠。
   在与李晓霞的决赛中，丁宁在2比3落后的情况下连扳两局，她以4比3（11比9、5比11、14比12、9比11、8比11、11比7、11比7）
   战胜李晓霞夺冠。这是中国代表团在里约的第10金。
   `;
    test.equal(UTF8.toString(UTF8.fromString(str)), str, 'same');
    for (let i=0; i<100; i++) {
      let strstr = UTF8.toString(UTF8.fromString(str));
    }
    test.done();
  },
  'test java to js event' : (test)=>{
    "use strict";

    let e = 'org.mhzed.bytepipe.evetn.bla-1';
    Mess.emitevent(e, 'msg').then();
    DeviceEventEmitter.addListener(e, (msg)=>{
      test.equal(msg, 'msg', 'got msg on ' + e);
      test.done();
    });
  },

  "print system dirs" : (test)=>{
    "use strict";
    Sys.sysDirs().then((dirs)=>{
      "use strict";
      test.ok(_.size(dirs)>0, "get stuff")
      TestDir = dirs['env.DIRECTORY_DCIM'];
      test.ok(TestDir, "test dir found")
    }).catch((e)=>{
      //console.log(e);
      test.ifError(e);
    }).then(test.done);
  },

  "read dir" : (test)=>{
    "use strict";
    let fsp = pmfy(fs);
    let srcDir = TestDir;
    //let d = "/storage/emulated/0";
    co(function*(){
      let files = yield fsp.readdir(srcDir)
      let i=0;
      for (let f of files) {
        try {
          let stat = yield fsp.stat(path.resolve(srcDir,f));
          i++;
        } catch (e) {
          console.log(f + " can't be stated " + e);
        }
      }
      test.ok(i>0, "read dirs")
    }).catch((err)=>{
      test.ifError(err);
    })
    .then(test.done);
  },

  "walk dir" : (test)=>{
    "use strict";
    // dont' walk the root dir in android, it's circularly linked, will result in stack overflow
    //let d = "/storage/emulated/0/DCIM";
    let srcDir = TestDir;
    let size = 0;
    fs.p.walkdir(srcDir, (f,r,s)=>{  // the filter function
      if (s.isFile()) {
        if (s.size < 4000) TestTextFile = f;    // find a small file as test file
        else if (s.size > 500000 ) {
          TestBinFile = f;
          size = s.size;
        }
      }
      return r; // default behavior
    })
    .then((files)=>{
      //console.log(files);
      test.ok(files.length>0, "walked dirs")
      if (TestBinFile) console.log("Found stream test file " + TestBinFile, ", " + size + " bytes");
    }).catch((err)=>{
      test.ifError(err);
    }).then(test.done);
  },

  "read file" : (test)=>{
    "use strict";
    if (!TestTextFile) {
      console.log("No small text file found, skipped");
      return test.done();
    }
    let testFile = TestTextFile;
    co(function*(){
      let stat = yield fs.p.stat(testFile);
      let fd = yield fs.p.open(testFile, "");
      let [buff,n] = yield fs.p.readBuff(fd, stat.size, 0);
      let strContent = UTF8.toString(buff);
      //console.log(strContent);
      yield fs.p.close(fd);
    }).catch((err)=>{
      test.ifError(err);
    }).then(test.done);
  },

  'native read write benchmark' : (test)=>{
    "use strict";
    let d = TestBinFile;
    if (!TestBinFile) {
      console.log("No larger file found, skipped");
      return test.done();
    }
    let outfile = outFileName(d, ".native");
    co(function*() {

      let fd = yield fs.p.open(d, "");
      let outfd = yield fs.p.open(outfile, "");
      yield fs.p.copy(fd, outfd, 4096);

      yield fs.p.close(fd);
      yield fs.p.close(outfd);
      yield fs.p.unlink(outfile);

      let n = yield fs.p.handleAllocSize();
      test.equal(n, 0, "all handles closed");
    }).catch((err)=>{
      test.ifError(err);
    }).then(test.done);
  },

  'stream file pipe benchmark' : (test)=>{
    "use strict";
    let d = TestBinFile;
    if (!TestBinFile) {
      console.log("No larger file found, skipped");
      return test.done();
    }
   let outfile = outFileName(d, ".pipe");
    co(function*() {
      let fd = yield fs.p.open(d, "");
      let outfd = yield fs.p.open(outfile, "");
      let rs = fs.createReadStream({fd});
      let ws = fs.createWriteStream({fd: outfd});
      rs.pipe(ws);    // nodejs style pipe, with all the base64 encoding overhead
      let onend = ws.on.bind(ws, 'finish');
      yield pmfy(onend)();
      // once ended, both stream should be closed automatically

      // yield future.once(1000);
      // let oldstat = yield fs.p.stat(d);
      // let ts = oldstat.mtime.getTime();
      // console.log(oldstat.mtime, ', ', ts);
      // let [rmtime,ismoded] = yield fs.p.utimes(outfile, ts, ts);
      // console.log(rmtime, ', ', ismoded);
      yield fs.p.unlink(outfile);
      let n = yield fs.p.handleAllocSize();
      test.equal(n, 0, "handles closed");
    }).catch(test.ifError).then(test.done);
  },
  'native file pipe benchmark' : (test)=>{
    "use strict";
    let srcFile = TestBinFile;
    if (!TestBinFile) {
      console.log("No larger file found, skipped");
      return test.done();
    }
    let outfile = outFileName(srcFile, ".nativepipe");
    co(function*() {
      let fd = yield fs.p.open(srcFile, "");
      let outfd = yield fs.p.open(outfile, "");
      let rs = fs.createReadStream({fd});
      let ws = fs.createWriteStream({fd: outfd});
      yield rs.pipeNative(ws, -1,0, null);  // pipe until end

      let n = yield fs.p.handleAllocSize();
      // n is two at this point, nativePipe does not auto close.
      yield fs.p.close(fd);
      yield fs.p.close(outfd);

      let s1 = yield fs.p.stat(srcFile);
      let s2 = yield fs.p.stat(outfile);
      test.equal(s1.size, s2.size, "same size");
      yield fs.p.unlink(outfile);

      n = yield fs.p.handleAllocSize();
      test.equal(n, 0, "all handles closed");

    }).catch(test.ifError).then(test.done);
  },


  /**
   * client send to server, server echo back, end connection on both sides.
   * @param cb
   */
  'tcp server echo test' : (test)=>{
    "use strict";
    let msg = "我是amigo";
    let cli;
    const server = net.createServer((conn)=>{
      co(function*(){
        conn.fdlog("server connection accepted");
        let recv = (cb)=>{
          conn.on('data', (buff)=>{cb(null, buff)});
        };
        let data = yield pmfy(recv)();
        data = UTF8.toString(data);
        test.equal(data, msg, 'received client msg');
        conn.fdlog("server received " + data);

        let reply = pmfy(conn.writeutf8, conn);
        let replymsg = data+ " echo";
        conn.fdlog("server echoing back " + replymsg);
        yield reply(replymsg);  // reply and end server conn

        yield pmfy(conn.end, conn)();    // wait for server conn to close

        conn.fdlog("waiting for client to close");
        let clientFinish = pmfy(cli.on.bind(cli, 'finish'));
        yield clientFinish(); // wait for end client conn

        conn.fdlog("waiting server listener to close");
        yield pmfy(server.close, server)(); // wait for server to close

        let n = yield fs.p.handleAllocSize(); // verify handels are all closed
        test.equal(n, 0, "all handles closed");

      }).then(test.done).catch(test.ifError);
    })
    server.listen(9898, '0.0.0.0', (err)=>{
      if (err) cb(err);
    })

    cli = net.connect(9898, "127.0.0.1", ()=>{

      cli.fdlog("client connection");

      cli.writeutf8(msg);
      cli.fdlog("client sending " + msg);
      cli.on('data', (data)=>{
        let echoedmsg = UTF8.toString(data);
        test.equal(echoedmsg, msg + " echo", 'recv echoed msg');
        cli.fdlog("client received " + echoedmsg);
      })
      cli.on('end', ()=>{ // client end!!
        cli.fdlog("client ending");
        cli.end();
      })
    })
  },

  /**
   * stream a file over tcp connection, in purse JS
   *
   * @param cb
   */

  'js net stream test' : (test)=>{
    "use strict";
    let srcFile = TestBinFile;
    if (!TestBinFile) {
      console.log("No larger file found, skipped");
      return test.done();
    }
    let outfile = outFileName(srcFile, ".netpipe");

    const server = net.createServer((conn)=>{
      co(function*(){

        let outfd = yield fs.p.open(outfile, "");
        let ws = fs.createWriteStream({fd: outfd});

        conn.pipe(ws);
        let onend = pmfy(ws.on.bind(ws, 'finish'));
        conn.fdlog('waiting for file ws to finish');
        yield onend();  // ws done writing, then conn reading must ended

        // but conn is only closed if conn.end results in 'finish'
        yield pmfy(conn.end, conn)();

        conn.fdlog('wait for server to close');
        yield pmfy(server.close, server)(); // wait for server to close

        let n = yield fs.p.handleAllocSize(); // verify handles are all closed
        test.equal(n, 0, "all handles closed");

        let s1 = yield fs.p.stat(srcFile);
        let s2 = yield fs.p.stat(outfile);
        test.equal(s1.size, s2.size, "same size");

        yield fs.p.unlink(outfile);         // clean up out file

      }).then(test.done).catch(test.ifError);
    })
    server.listen(9898, '0.0.0.0', (err)=>{
      test.ifError(err);
    })

    let cli = net.connect(9898, "127.0.0.1", ()=>{
      co(function*() {
        let fd = yield fs.p.open(srcFile, "");
        let rs = fs.createReadStream({fd});
        rs.pipe(cli);
        let onend = pmfy(cli.on.bind(cli, 'finish'));
        yield onend();
        cli.fdlog("client closed");
      }).then();
    });
  },

  // client 1 -> server -> client 2
  // test: file -> socket -> socket -> file streaming
  'native net stream test' : (test)=>{
    "use strict";
    let srcFile = TestBinFile;
    if (!TestBinFile) {
      console.log("No larger file found, skipped");
      return test.done();
    }
    let outfile = outFileName(srcFile, ".netnativepipe");

    let conn1, conn2;
    // pause read on connection, resume once both connections are established.
    const server = net.createServer({pauseOnConnect:true}, (conn)=>{
      conn.fdlog("server conn");
      if (conn1 === undefined) {
        conn1 = conn;
      }
      else {
        conn2 = conn;
        co(function*(){
          let [eof, bytesPiped] = yield conn1.pipeNative(conn2, -1, 0, null);

          conn1._pauseRead(false);
          conn2._pauseRead(false);

          test.equal(eof, true, 'conn eof must be reached');
          conn2.fdlog('server piped ' + bytesPiped);

          conn1.fdlog('waiting to end client1');
          yield pmfy(conn1.end, conn1)();

          conn2.fdlog('waiting to end client 2');
          yield pmfy(conn2.end, conn2)();

          conn.fdlog('wait for server to close');
          yield pmfy(server.close, server)(); // wait for server to close

          yield future.once(5);
          let n = yield fs.p.handleAllocSize(); // verify handles are all closed
          test.equal(n, 0, "all handles closed");

          // verify files are same size
          let s1 = yield fs.p.stat(srcFile);
          let s2 = yield fs.p.stat(outfile);
          test.equal(s1.size, s2.size, "same size");
          yield fs.p.unlink(outfile);         // clean up out file

        }).then(test.done).catch(test.ifError);
      }
    })

    server.listen(9898, '0.0.0.0', (err)=>{
      test.ifError(err);
    })

    let cli = net.connect(9898, "127.0.0.1", ()=>{
      cli.fdlog("client conn");
      co(function*() {

        let fd = yield fs.p.open(srcFile, "");
        let rs = fs.createReadStream({fd});
        let [eof, bytesPiped] = yield rs.pipeNative(cli, -1,0, null);

        test.equal(eof, true, 'file eof must be reached');
        cli.fdlog("client piped " + bytesPiped );

        yield fs.p.close(fd);

        yield pmfy(cli.end,cli)();
        cli.fdlog("client closed");
        //yield fs.p.close(fd);
      }).then();
    });
    let cli2 = net.connect(9898, "127.0.0.1", ()=>{
      cli2.fdlog("client conn 2");
      co(function*() {

        let outfd = yield fs.p.open(outfile, "");
        let ws = fs.createWriteStream({fd: outfd});

        let [eof, bytesPiped] = yield cli2.pipeNative(ws, -1, 100000, (npiped)=>{
          //console.log(`${npiped} bytes piped`);
        });
        test.equal(eof, true, 'conn eof must be reached');
        cli2.fdlog('client 2 piped ' + bytesPiped);

        yield fs.p.close(outfd);

        yield pmfy(cli2.end,cli2)();
        cli2.fdlog("client 2 closed");
      }).then();
    });

  },

  'pause netconn read' : (test)=>{
    "use strict";
    let msg = "我是amigo";
    let cli;
    const server = net.createServer((conn)=>{
      co(function*(){
        conn.fdlog("server connection accepted");

        conn.fdlog("pausing server read");
        conn._pauseRead(true);
        //yield pause(true);

        yield future.once(2000);
        conn.fdlog("resuming server read");
        conn._pauseRead(false);

        let recv = (cb)=>{
          conn.on('data', (buff)=>{cb(null, buff)});
        };
        let data = yield pmfy(recv)();
        data = UTF8.toString(data);
        test.equal(data, msg, 'received client msg');
        conn.fdlog("server received " + data);

        yield pmfy(conn.end, conn)();    // wait for server conn to close

        conn.fdlog("waiting for client to close");
        let clientFinish = pmfy(cli.on.bind(cli, 'finish'));
        yield clientFinish(); // wait for end client conn

        conn.fdlog("waiting server listener to close");
        yield pmfy(server.close, server)(); // wait for server to close

        let n = yield fs.p.handleAllocSize(); // verify handels are all closed
        test.equal(n, 0, "all handles closed");

      }).then(test.done).catch(test.ifError);
    })
    server.listen(9898, '0.0.0.0', (err)=>{
      if (err) cb(err);
    })

    cli = net.connect(9898, "127.0.0.1", ()=>{
      co(function*(){

        cli.fdlog("client connection");
        yield future.once(10);
        cli.writeutf8(msg);
        cli.fdlog("client sending " + msg);
        cli.on('data', (data)=>{
        })
        cli.on('end', ()=>{ // client end!!
          cli.fdlog("client ending");
          cli.end();
        })
      }).then();
    })
  },

  'end' : (test)=>{
    "use strict";
    // ensure daemon thread not looping too much
    future.once(2000, ()=>{
      Net.selectLoopLog(false, test.done);
    })

  },

}

//delete tests['native read write benchmark'];  // for debugging
//delete tests['stream file pipe benchmark'];   // too slow
//delete tests['js net stream test'];       // too slow
//delete tests['pause netconn read'];

module.exports = tests;

