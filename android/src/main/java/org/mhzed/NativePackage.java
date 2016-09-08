package org.mhzed;

import com.facebook.react.bridge.NativeModule;
import com.facebook.react.bridge.JavaScriptModule;
import com.facebook.react.uimanager.ViewManager;
import com.facebook.react.bridge.ReactApplicationContext;
import com.facebook.react.bridge.ReactContext;
import com.facebook.react.bridge.ReactContextBaseJavaModule;
import com.facebook.react.bridge.ReactMethod;
import com.facebook.react.ReactPackage;

import org.mhzed.FsModule;
import org.mhzed.MessModule;

import java.util.List;
import java.util.Collections;
import java.util.ArrayList;

/*
 * In MainApplicaiton.java, modify MainApplication.getPackages, return additional "new NativePackage()"
 */
public class NativePackage implements ReactPackage {

  // JavaScriptModule is for mapping js module into Java, dont' do it.  Instead use
  // com.facebook.react.modules.core.RCTNativeAppEventEmitter,
  @Override
  public List<Class<? extends JavaScriptModule>> createJSModules() {
    return Collections.emptyList();
  }

  @Override
  public List<ViewManager> createViewManagers(ReactApplicationContext reactContext) {
    return Collections.emptyList();
  }

  @Override
  public List<NativeModule> createNativeModules(
                              ReactApplicationContext reactContext) {
    List<NativeModule> modules = new ArrayList<>();

    modules.add(new FsModule(reactContext));
    modules.add(new SysModule(reactContext));
    modules.add(new MessModule(reactContext));
    modules.add(new NetModule(reactContext));

    return modules;
  }

}