
import {
  NativeModules
} from 'react-native';
const {Sys} = NativeModules;

let os = require("os");
let co = require("co");
let _networkInterfaces = {};

co(function*() {
  "use strict";
  _networkInterfaces = yield Sys.getLocalIpAddress();
}).then();


os.networkInterfaces = ()=>{
  return _networkInterfaces;
}

module.exports = os;