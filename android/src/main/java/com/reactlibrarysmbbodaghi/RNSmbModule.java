
package com.reactlibrarysmbbodaghi;

import com.facebook.react.bridge.Arguments;
import com.facebook.react.bridge.ReactApplicationContext;
import com.facebook.react.bridge.ReactContext;
import com.facebook.react.bridge.ReactContextBaseJavaModule;
import com.facebook.react.bridge.ReactMethod;
import com.facebook.react.bridge.Callback;
import com.facebook.react.bridge.ReadableMap;
import com.facebook.react.bridge.WritableMap;
import com.facebook.react.modules.core.DeviceEventManagerModule;

import android.widget.Toast;
import android.text.TextUtils;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import javax.annotation.Nullable;

import jcifs.smb.NtlmPasswordAuthentication;
import jcifs.smb.SmbFile;
import jcifs.smb.SmbFileOutputStream;

public class RNSmbModule extends ReactContextBaseJavaModule {

  private final ReactApplicationContext reactContext;

  private NtlmPasswordAuthentication authentication;
  private String serverURL = "smb://server_ip:server_port/shared_folder";


  public RNSmbModule(ReactApplicationContext reactContext) {
    super(reactContext);
    this.reactContext = reactContext;
  }

  @Override
  public String getName() {
    return "RNSmb";
  }


  /**
   * private functions
   */

  //for event
  private void sendEvent(ReactContext reactContext,
                         String eventName,
                         @Nullable WritableMap params) {
    reactContext
            .getJSModule(DeviceEventManagerModule.RCTDeviceEventEmitter.class)
            .emit(eventName, params);
  }


  /**
   * react native bridge functions
   */

  @ReactMethod
  public void show(String text) {
    //ReactApplicationContext context = getReactApplicationContext();
    Toast.makeText(getReactApplicationContext(), text, Toast.LENGTH_SHORT).show();
  }

  @ReactMethod
  public void init(ReadableMap options, Callback successCallback, Callback errorCallback) {
    String workGroup = "", username = "", password = "";
    String ip = "", port = "", sharedFolder = "";

    try {
      workGroup = options.hasKey("workGroup") ? options.getString("workGroup") : "";
      username = options.hasKey("username") ? options.getString("username") : "";
      password = options.hasKey("password") ? options.getString("password") : "";
      ip = options.hasKey("ip") ? options.getString("ip") : "";
      port = options.hasKey("port") ? options.getString("port") : "";
      sharedFolder = options.hasKey("sharedFolder") ? options.getString("sharedFolder") : "";
    } catch (Exception e) {
      //options structure error
      e.printStackTrace();
      errorCallback.invoke("invalid options structure: " + e.getMessage());
      return;
    }

    try {
      if (!TextUtils.isEmpty(username) && !TextUtils.isEmpty(password)) {
        authentication = new NtlmPasswordAuthentication(workGroup, username, password);
      }
    } catch (Exception e) {
      // NtlmPasswordAuthentication creation error
      e.printStackTrace();
      errorCallback.invoke("exception in initializing authentication: " + e.getMessage());
      return;
    }

    try {
      if (!TextUtils.isEmpty(ip)) {
        String server_url = "smb://" + ip;
        if (!TextUtils.isEmpty(port)) {
          server_url += ":" + port;
        }
        if (!TextUtils.isEmpty(sharedFolder)) {
          server_url += "/" + sharedFolder;
        }
        serverURL = server_url;
        successCallback.invoke(serverURL);
        return;
      }
    } catch (Exception e) {
      // server url initializing error
      e.printStackTrace();
      errorCallback.invoke("exception in initializing server url: " + e.getMessage());
      return;
    }

  }

  @ReactMethod
  public void testConnection() {
    WritableMap params = Arguments.createMap();

    try {

      SmbFile sFile;
      if (authentication != null) {
        sFile = new SmbFile(serverURL, authentication);
      } else {
        sFile = new SmbFile(serverURL);
      }

      String message = "can read" + " : " + String.valueOf(sFile.canRead());
      params.putBoolean("success", true);
      params.putString("message", "server [" + serverURL + "] can accessible.");

    } catch (Exception e) {
      // Output the stack trace.
      e.printStackTrace();
      params.putBoolean("success", false);
      params.putString("message", "exception error: " + e.getMessage());
    }
    sendEvent(reactContext, "TestConnection", params);
  }

  @ReactMethod
  public void test(String workGroup, String ip, String username, String password, String sharedFolder, String fileName) {
    try {
      NtlmPasswordAuthentication auth = new NtlmPasswordAuthentication(workGroup, username, password);

      SmbFile sFile;
      String url = "smb://" + ip + "/" + sharedFolder + "/" + fileName;
      if (!TextUtils.isEmpty(username) && !TextUtils.isEmpty(password)) {
        sFile = new SmbFile(url, auth);
      } else {
        sFile = new SmbFile(url);
      }

      String message = "can read" + " : " + String.valueOf(sFile.canRead());

      Toast.makeText(getReactApplicationContext(), message, Toast.LENGTH_SHORT).show();
    } catch (Exception e) {
      // Output the stack trace.
      e.printStackTrace();
      String message = " error in testing SMB";
    }
  }

}
