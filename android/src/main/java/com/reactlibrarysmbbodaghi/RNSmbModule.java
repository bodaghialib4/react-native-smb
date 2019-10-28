
package com.reactlibrarysmbbodaghi;

import com.facebook.react.bridge.ReactApplicationContext;
import com.facebook.react.bridge.ReactContextBaseJavaModule;
import com.facebook.react.bridge.ReactMethod;
import com.facebook.react.bridge.Callback;
import android.widget.Toast;
import android.text.TextUtils;


import jcifs.smb.NtlmPasswordAuthentication;
import jcifs.smb.SmbFile;
import jcifs.smb.SmbFileOutputStream;

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
    Toast.makeText(getReactApplicationContext(), text, Toast.LENGTH_SHORT).show();
  }

  @ReactMethod
  public void test(String workGroup, String ip, String username, String password, String sharedFolder,String fileName) {
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
