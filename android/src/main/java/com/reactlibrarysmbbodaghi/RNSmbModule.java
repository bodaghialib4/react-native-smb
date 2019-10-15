
package com.reactlibrarysmbbodaghi;

import com.facebook.react.bridge.ReactApplicationContext;
import com.facebook.react.bridge.ReactContextBaseJavaModule;
import com.facebook.react.bridge.ReactMethod;
import com.facebook.react.bridge.Callback;
import android.widget.Toast;

public class RNSmbModule extends ReactContextBaseJavaModule {

  private final ReactApplicationContext reactContext;

  public RNSmbModule(ReactApplicationContext reactContext) {
    super(reactContext);
    this.reactContext = reactContext;
  }

  @Override
  public String getName() {
    return "RNSmb";
  }


  @ReactMethod
  public void show(String text) {
    //ReactApplicationContext context = getReactApplicationContext();
    Toast.makeText(getReactApplicationContext(), text, Toast.LENGTH_LONG).show();
  }

}