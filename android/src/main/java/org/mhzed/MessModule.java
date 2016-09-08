package org.mhzed;

import com.facebook.react.bridge.NativeModule;
import com.facebook.react.bridge.ReactApplicationContext;
import com.facebook.react.bridge.ReactContext;
import com.facebook.react.bridge.ReactContextBaseJavaModule;
import com.facebook.react.bridge.ReactMethod;
import com.facebook.react.bridge.Promise;
import com.facebook.react.bridge.Callback;
import com.facebook.react.bridge.ReadableArray;
import com.facebook.react.bridge.ReadableMap;
import com.facebook.react.bridge.WritableNativeArray;
import com.facebook.react.bridge.WritableNativeMap;
import com.facebook.react.modules.core.DeviceEventManagerModule;

import java.util.Map;
import java.util.ArrayList;

import java.io.StringWriter;
import java.io.PrintWriter;

import java.io.RandomAccessFile;
import java.io.File;
import android.util.Base64;
import android.os.Environment;
import android.support.v4.content.ContextCompat;
import android.content.Context;

//import java.nio.file.Files;

/**
 * For messing around, testing ideas.
 */
public class MessModule extends ReactContextBaseJavaModule {

  private android.content.Context _ctx;
  private ReactApplicationContext reactContext;

  public MessModule(ReactApplicationContext reactContext) {
    super(reactContext);
    this._ctx = reactContext.getApplicationContext();
    this.reactContext = reactContext;
  }

  @Override
  public String getName() {
    return "Mess";
  }

  // for testing if java can invoke multiple call backs in JS, answer: no you can't, one callback only
  @ReactMethod
  public void cbtest(Callback cb1, Callback cb2, Callback cb3) {
    try {
      for (int i=0; i<10; i++) {
        //cb1.invoke();
        //if (i%2 == 0) cb2.invoke();
      }
      cb3.invoke();
    } catch (Exception e) {
      cb3.invoke(Util.strace(e));
    }
  }

  // convert a double to long via round, then return string back to JS runtime, ensure no precision is lost.
  @ReactMethod
  public void echoDouble(Double d, Promise promise) {
    promise.resolve(Long.toString(Math.round(d)));
  }
  @ReactMethod
  public void callbackInThread(final Callback cb) {
    Thread t = new Thread(new Runnable() {
      @Override
      public void run() {
        cb.invoke();
      }
    });
    t.start();
  }

  @ReactMethod
  public void emitevent(String eventName, String msg, Promise p) {
    reactContext
        .getJSModule(DeviceEventManagerModule.RCTDeviceEventEmitter.class)
        .emit(eventName, msg);
    p.resolve(1);
  }


}