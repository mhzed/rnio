/**
 * To keep track of all io handles, for debugging purpose.
 */
const _ = require("lodash");
let gpool = [];

let debug;
if (typeof process === 'undefined') {
  debug = false;
} else {
  debug = process.env['NODE_DEBUG'] ? true : false;
}
module.exports = {

  pool() {
    "use strict";
    return gpool;
  },

  add(c) {
    "use strict";
    let loc;
    if (debug) loc = (new Error()).stack.split("\n").slice(1);
    gpool.push({
      c,
      loc
    });
  },

  del(c) {
    "use strict";
    _.remove(gpool, (e)=>e.c === c);
  }

}