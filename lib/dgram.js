/**
 * Created by mhzed on 2016-08-17.
 */

import {
  NativeModules,
  DeviceEventEmitter
} from 'react-native';

const {Net} = NativeModules;
const dgram = {

  createSocket(option, datacb) {
    "use strict";
    let sock =  new DgramSock(datacb);
    return sock;
  }

}

module.exports = dgram;