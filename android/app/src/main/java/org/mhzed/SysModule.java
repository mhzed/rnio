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

import java.io.FileOutputStream;
import java.net.Inet6Address;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.util.Enumeration;
import java.util.Map;
import java.util.ArrayList;

import java.io.StringWriter;
import java.io.PrintWriter;

import java.io.RandomAccessFile;
import java.io.File;
import java.util.UUID;

import android.util.Base64;
import android.os.Environment;
import android.support.v4.content.ContextCompat;
import android.content.Context;

public class SysModule extends ReactContextBaseJavaModule {

  private android.content.Context _ctx;
  private ReactApplicationContext reactContext;

  public SysModule(ReactApplicationContext reactContext) {
    super(reactContext);
    this._ctx = reactContext.getApplicationContext();
    this.reactContext = reactContext;
  }

  @Override
  public String getName() {
    return "Sys";
  }

  @ReactMethod
  public void deviceModel(Promise promise) {
    promise.resolve(android.os.Build.MODEL);
  }

  // see http://android-developers.blogspot.co.id/2011/03/identifying-app-installations.html
  // the uuid is randomly generated and saved in app's dir.  It's reset when phone is reset but not when
  // app is uninstalled
  private static String _sid = null;
  @ReactMethod
  public void appDeviceUuid(Promise promise) {
    try {
      if (_sid == null) {
        File installation = new File(_ctx.getFilesDir(), "INSTALLATION");
        if (!installation.exists()) {   // generate and save
          FileOutputStream out = new FileOutputStream(installation);
          String id = UUID.randomUUID().toString();
          out.write(id.getBytes());
          out.close();
        } // read uuid
        RandomAccessFile f = new RandomAccessFile(installation, "r");
        byte[] bytes = new byte[(int) f.length()];
        f.readFully(bytes);
        f.close();
        _sid = new String(bytes);
      }
      promise.resolve(_sid);
    } catch (Exception e) {
      promise.reject(e);
    }
  }

  /**
   * <uses-permission android:name="android.permission.INTERNET" />
   * <uses-permission android:name="android.permission.ACCESS_NETWORK_STATE" />
   * returns an object similar to what nodejs os.networkInterfaces() returns;
   * @param promise { interfaceName : [ip1, ...] }
   */
  @ReactMethod
  public void getLocalIpAddress(Promise promise){
    WritableNativeMap ret = new WritableNativeMap();
    try {
      for (Enumeration<NetworkInterface> en = NetworkInterface.getNetworkInterfaces();
           en.hasMoreElements();) {
        NetworkInterface intf = en.nextElement();
        WritableNativeArray ips = new WritableNativeArray();

        for (Enumeration<InetAddress> enumIpAddr = intf.getInetAddresses(); enumIpAddr.hasMoreElements();) {

          InetAddress inetAddress = enumIpAddr.nextElement();
          WritableNativeMap ipobj = new WritableNativeMap();
          ipobj.putString("address", inetAddress.getHostAddress().toString());
          //ipobj.putString("netmask", inetAddress.get);
          ipobj.putString("family", inetAddress instanceof Inet6Address ? "IPv6" : "IPv4");
          //ipobj.putString("mac", intf.getHardwareAddress() );
          ipobj.putBoolean("internal", inetAddress.isLoopbackAddress());

          ips.pushMap(ipobj);

        }
        ret.putArray(intf.getName(), ips);
      }
      promise.resolve(ret);
    } catch (Exception ex) {
      promise.reject(ex);
    }

  }
  /**  Return a map of system directories
   */
  @ReactMethod
  public void sysDirs(Promise promise) {
    WritableNativeMap ret = new WritableNativeMap();

    // due to android system version, some apis may not be available, in such case Throwable is thrown
    // catch and ignore
    try {
      File[] files = android.support.v4.content.ContextCompat.getExternalFilesDirs(this._ctx, null);
      WritableNativeArray dirs = new WritableNativeArray();
      for (File f : files) dirs.pushString(f.toString());
      ret.putArray("ContextCompat.getExternalFilesDirs", dirs);
    } catch (Throwable e) {};

    try {
      ret.putString("app.android.context.getFilesDir",
          this._ctx.getFilesDir().toString());
    } catch (Throwable e) {};
    try {
      ret.putString("env.DIRECTORY_DCIM",
          Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DCIM).toString());
    } catch (Throwable e) {};
    try {
      ret.putString("env.DIRECTORY_ALARMS",
          Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_ALARMS).toString());
    } catch (Throwable e) {};
    try {
      ret.putString("env.DIRECTORY_DOCUMENTS",
          Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS).toString());
    } catch (Throwable e) {};
    try {
      ret.putString("env.DIRECTORY_DOWNLOADS",
          Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS).toString());
    } catch (Throwable e) {};
    try {
      ret.putString("env.DIRECTORY_MOVIES",
          Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MOVIES).toString());
    } catch (Throwable e) {};
    try {
      ret.putString("env.DIRECTORY_MUSIC",
          Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MUSIC).toString());
    } catch (Throwable e) {};
    try {
      ret.putString("env.DIRECTORY_PICTURES",
          Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES).toString());
    } catch (Throwable e) {};
    try {
      ret.putString("env.DIRECTORY_PODCASTS",
          Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PODCASTS).toString());
    } catch (Throwable e) {};
    try {
      ret.putString("env.DIRECTORY_RINGTONES",
          Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_RINGTONES).toString());
    } catch (Throwable e) {};
    try {
      ret.putString("env.DIRECTORY_NOTIFICATIONS",
          Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_NOTIFICATIONS).toString());
    } catch (Throwable e) {};

    try {
      ret.putString("android.os.Environment.getDataDirectory",
          Environment.getDataDirectory().toString());
    } catch (Throwable e) {};
    try {
      ret.putString("android.os.Environment.getDownloadCacheDirectory",
          Environment.getDownloadCacheDirectory().toString());
    } catch (Throwable e) {};
    try {
      ret.putString("android.os.Environment.getExternalStorageDirectory",
          Environment.getExternalStorageDirectory().toString());
    } catch (Throwable e) {};
    try {
      ret.putString("android.os.Environment.getRootDirectory",
          Environment.getRootDirectory().toString());
    } catch (Throwable e) {};


    promise.resolve(ret);
  }


}