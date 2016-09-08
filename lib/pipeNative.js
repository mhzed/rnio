import {
  NativeModules,
  DeviceEventEmitter
} from 'react-native';
const {Net} = NativeModules;
const NetEvents = require("./NetNativeEvents");


/**
 * for performance, we tap into native pipe implementation
 * pipeNative does not support chaining, both stream must be native stream (not js backed
 * stream), when pipe is finished, finishcb is called
 * pipeNative does not auto close source and sink streams, you need to close them manually
 *
 * during piping, 'data' event is not emitted on source
 * after piping, srcfd is automatically paused from reading.  For NetConn, call _pauseRead(false)
 * to resume reading: firing 'data' events
 *
 * @param srcfd   pipe from this native stream fd
 * @param dstfd   to this
 * @param size    how many bytes to pipe, -1 to pipe until srcfd eof reached
 * @param progressChunkSize  in-between progresscb call there are at least this many bytes transferred
 *                           0 means do not call progresscb
 * @param progresscb  (nBytesPiped) progress callback function.  progresscb involves native environment
 *                   firing events to JS runtime, there is non-negligible overhead, so select a
 *                   reasonably large progressChunkSize
 * @param finishcb   (err, eof, nBytePiped): eof is if srcfd's eof is reached.
 */
module.exports = (srcfd, dstfd, size, progressChunkSize, progresscb, finishcb) => {
  "use strict";

  if (progressChunkSize>0 && progresscb) {
    DeviceEventEmitter.addListener(NetEvents.PIPE_BYTES(srcfd), progresscb);
    Net.pipe(srcfd, dstfd, size, progressChunkSize, (err, eof, nBytesPiped)=>{
      DeviceEventEmitter.removeAllListeners(NetEvents.PIPE_BYTES(srcfd));
      finishcb(err, eof, nBytesPiped);
    });
  } else
    Net.pipe(srcfd, dstfd, size, progressChunkSize, finishcb);
}
