/**
 * Created by mhzed on 2016-07-20.
 */
import {
  NativeModules,
  DeviceEventEmitter
} from 'react-native';
/**
 *  To the extent possible, mirror nodejs fs api
 */
const co = require("co");
const NetServer = require("./NetServer");
const NetConn = require("./NetConn");
const pmfy = require("pmfy");
const iopool = require("./iopool");

const net = {

  /**
   * Currently all sockets are in { allowHalfOpen: true } mode.  Socket is not closed until end() is
   * called specifically.
   *
   *
   * @param opts {
                    allowHalfOpen: false,
                    pauseOnConnect: false
                  }
   * @param connectionCb
   * @returns {NetServer}
   */
  createServer(opts, connectionCb) {
    "use strict";
    return new NetServer(opts, connectionCb);
  },

  connect(port, host, listener) {
    "use strict";
    if (typeof host =='function') {
      listener = host;
      host = undefined;
    }
    let cli = new NetConn();
    cli.connect({port, host}, listener);
    return cli;
  },

  pool() {
    "use strict";
    return iopool.pool();
  }
}

// promises
net.p = {

  findFreePorts(start, end, host, n) {
    "use strict";
    return co(function*() {
      const test = pmfy((port, host, cb)=>{
        let sockServer = net.createServer();
        sockServer.listen(port, host, (err)=>{
          if (!err) {
            sockServer.close(cb);
          }
          else cb(err);
        });
      })
      let freeports = [];
      for (let port = start; port < end; port++) {
        if (freeports.length >=n ) break;
        try {
          yield test(port, host);
          freeports.push(port);
        } catch (e) {
        }
      }
      return freeports;
    });

  }
}
module.exports = net;