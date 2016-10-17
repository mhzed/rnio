import {
  NativeModules
} from 'react-native';
const {Sys} = NativeModules;

module.exports = {
  fs : require("./lib/fs"),
  net : require("./lib/net"),
  //dgram : require("./lib/dgram"),

  os : require("./lib/os"),

  sys : Sys,

  // for testing
  tester : require("./lib/tester"),
  test : require("./test/fs.test"),


}