/**
 * Runs test as nodeunit would.
 *
 * Test is an object where if value is a function, then it's assumed to be a test case.  If value is
 * an object, then it's assumed to be another test suite (recursively nested).
 *
 */
const co = require("co")
const _ = require("lodash")
const pmfy = require("pmfy")
const assert = require("assert");

const tester = {

  run(test) {
    "use strict";
    return tester._run(test);
  },

  // internal helpers
  _run(test, kprefix, runtime){
    if (!kprefix) kprefix = '';
    if (!runtime) runtime = tester._runtime();

    return co(function*() {
      "use strict";
      for (let k of _.keys(test)) {
        let keyname = `${kprefix}${k}`;
        let v = test[k];
        if (typeof v === 'object')
          yield tester._run(v, keyname+'.', runtime);
        else if (typeof v === 'function') {
          console.log(`---${keyname} begins---`);
          let ts = Date.now();

          let runtest = pmfy((cb)=>{
            runtime._donecb = cb;
            v(runtime);
          });
          yield runtest();

          console.log(`---${keyname} done in ${(Date.now() - ts) / 1000} s---`);
        }
      }
    }).catch((e)=> {
      "use strict";
      console.log('---Test aborted due to err ' + e);
    });
  },

  _runtime() {
    "use strict";
    let rt = _.assign({}, assert);
    rt.done = ()=>{
      rt._donecb();
    }
    return rt;
  }
}

module.exports = tester;
//require("./tester")(tests);