
package com.reactlibrarysmbbodaghi;

import com.facebook.react.bridge.Arguments;
import com.facebook.react.bridge.ReactApplicationContext;
import com.facebook.react.bridge.ReactContext;
import com.facebook.react.bridge.ReactContextBaseJavaModule;
import com.facebook.react.bridge.ReactMethod;
import com.facebook.react.bridge.Callback;
import com.facebook.react.bridge.ReadableMap;
import com.facebook.react.bridge.WritableArray;
import com.facebook.react.bridge.WritableMap;
import com.facebook.react.modules.core.DeviceEventManagerModule;

import android.Manifest;
import android.app.Activity;
import android.content.pm.PackageManager;
import android.os.Environment;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.widget.Toast;
import android.text.TextUtils;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStream;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;


import javax.annotation.Nullable;

import jcifs.smb.NtlmPasswordAuthentication;
import jcifs.smb.SmbFile;
import jcifs.smb.SmbFileOutputStream;

public class RNSmbModule extends ReactContextBaseJavaModule {


  public static final int PERMISSIONS_REQUEST_CODE = 0;
  private static final int REQUEST_EXTERNAL_STORAGE = 1;
  private static String[] PERMISSIONS_STORAGE = {
          Manifest.permission.READ_EXTERNAL_STORAGE,
          Manifest.permission.WRITE_EXTERNAL_STORAGE
  };

  private final ReactApplicationContext reactContext;

  private NtlmPasswordAuthentication authentication;
  private String serverURL = "smb://server_ip:server_port/shared_folder";

  private static LinkedBlockingQueue<Runnable> downloadTaskQueue = new LinkedBlockingQueue<>();
  private static ThreadPoolExecutor downloadThreadPool = new ThreadPoolExecutor(2, 10, 5000,  TimeUnit.MILLISECONDS, downloadTaskQueue);

  public RNSmbModule(ReactApplicationContext reactContext) {
    super(reactContext);
    this.reactContext = reactContext;
  }

  @Override
  public String getName() {
    return "RNSmb";
  }


  /**
   * **************** private functions ******************
   */

  private boolean checkReadExternalStoragePermissions() {
    String permission = Manifest.permission.READ_EXTERNAL_STORAGE;

    if (ContextCompat.checkSelfPermission(getCurrentActivity(), permission) != PackageManager.PERMISSION_GRANTED) {
      if (ActivityCompat.shouldShowRequestPermissionRationale(getCurrentActivity(), permission)) {
        Toast.makeText(getCurrentActivity(), "Allow external storage reading", Toast.LENGTH_SHORT).show();
        return false;
      } else {
        ActivityCompat.requestPermissions(getCurrentActivity(), new String[]{permission}, PERMISSIONS_REQUEST_CODE);
        return true;
      }
    }
    return true;
  }

  private boolean checkWriteExternalStoragePermissions() {
    String permission = Manifest.permission.WRITE_EXTERNAL_STORAGE;

    if (ContextCompat.checkSelfPermission(getCurrentActivity(), permission) != PackageManager.PERMISSION_GRANTED) {
      if (ActivityCompat.shouldShowRequestPermissionRationale(getCurrentActivity(), permission)) {
        Toast.makeText(getCurrentActivity(), "Allow external storage Writing", Toast.LENGTH_SHORT).show();
        return false;
      } else {
        ActivityCompat.requestPermissions(getCurrentActivity(), new String[]{permission}, PERMISSIONS_REQUEST_CODE);
        return true;
      }
    }
    return true;
  }



  /**
   * Checks if the app has permission to write to device storage
   * <p>
   * If the app does not has permission then the user will be prompted to grant permissions
   *
   * @param activity
   */
  public static void verifyStoragePermissions(Activity activity) {
    // Check if we have write permission
    int permission = ActivityCompat.checkSelfPermission(activity, Manifest.permission.WRITE_EXTERNAL_STORAGE);

    if (permission != PackageManager.PERMISSION_GRANTED) {
      // We don't have permission so prompt the user
      ActivityCompat.requestPermissions(
              activity,
              PERMISSIONS_STORAGE,
              REQUEST_EXTERNAL_STORAGE
      );
    }
  }


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
    sendEvent(reactContext, "SMBTestConnection", params);
  }

  @ReactMethod
  public void list(@Nullable String path) {
    WritableMap params = Arguments.createMap();
    try {

      String destinationPath = "/";
      if (path != null && !TextUtils.isEmpty(path)) {
        destinationPath = "/" + path + destinationPath;
      }
      SmbFile sFile;
      if (authentication != null) {
        sFile = new SmbFile(serverURL + destinationPath, authentication);
      } else {
        sFile = new SmbFile(serverURL + destinationPath);
      }


      if (sFile.canRead() && sFile.exists()) {
        if (sFile.isDirectory()) {
          List<SmbFile> files = Arrays.asList(sFile.listFiles());
          WritableArray list = Arguments.createArray();
          for (SmbFile file : files) {
            WritableMap currentFile = Arguments.createMap();
            currentFile.putString("name", file.getName());

            //boolean
            currentFile.putBoolean("isDirectory", file.isDirectory());
            currentFile.putBoolean("isFile", file.isFile());
            currentFile.putBoolean("canRead", file.canRead());
            currentFile.putBoolean("canWrite", file.canWrite());
            currentFile.putBoolean("isHidden", file.isHidden());

            //long
            currentFile.putString("createTime", String.valueOf(file.createTime()));
            currentFile.putString("getDate", String.valueOf(file.getDate()));
            currentFile.putString("getLastModified", String.valueOf(file.getLastModified()));
            currentFile.putString("lastModified", String.valueOf(file.lastModified()));

            //string
            currentFile.putString("getParent", file.getParent());
            currentFile.putString("getPath", file.getPath());
            currentFile.putString("getShare", file.getShare());
            currentFile.putString("getServer", file.getServer());

            //object
            //currentFile.putMap("getContent",file.getContent());

            //int
            currentFile.putInt("getType", file.getType());

            list.pushMap(currentFile);

//            if (file.isDirectory()) {
//              System.out.println("Directory: " + file.getName());
//            }
//            if (file.isFile()) {
//              System.out.println("File: " + file.getName());
//            }
          }

          params.putBoolean("success", true);
          params.putString("message", "path [" + path + "] list successfully.");
          params.putArray("list", list);
        } else {
          params.putBoolean("success", false);
          params.putString("message", "path [" + path + "] is not a directory.");
        }
      } else {
        params.putBoolean("success", false);
        params.putString("message", "can not read [" + path + "] directory.");
      }
    } catch (Exception e) {
      // Output the stack trace.
      e.printStackTrace();
      params.putBoolean("success", false);
      params.putString("message", "exception error: " + e.getMessage());
    }
    sendEvent(reactContext, "SMBList", params);
  }

  @ReactMethod
  public void download(
          @Nullable final String path,
          final String fileName
          ) {

    if(checkWriteExternalStoragePermissions()) {
      downloadThreadPool.execute(new Runnable() {
        @Override
        public void run() {

        WritableMap statusParams = Arguments.createMap();
        try {
          //verifyStoragePermissions(getCurrentActivity());
          String destinationPath = "/";
          if (path != null && !TextUtils.isEmpty(path)) {
            destinationPath = "/" + path + destinationPath;
          }
          if (fileName != null && !TextUtils.isEmpty(fileName)) {
            destinationPath = destinationPath + fileName;
          }
          SmbFile sFile;
          if (authentication != null) {
            sFile = new SmbFile(serverURL + destinationPath, authentication);
          } else {
            sFile = new SmbFile(serverURL + destinationPath);
          }


          File destFile = new File("");
//      if (TextUtils.isEmpty(fileName)) {
//        if (sFile.isDirectory()) {
//          destFile = getFolder(sFile);
//        }
          if (sFile.isDirectory()) {
            statusParams.putBoolean("success", false);
            statusParams.putString("errorMessage", " [destinationPath] is a directory!!");
          } else if (!sFile.exists()) {
            statusParams.putBoolean("success", false);
            statusParams.putString("errorMessage", " [destinationPath] is not exist directory!!");
          } else if (!sFile.canRead()) {
            statusParams.putBoolean("success", false);
            statusParams.putString("errorMessage", " no permission  to read [destinationPath]!!");
          } else {
            BufferedInputStream inBuf = new BufferedInputStream(sFile.getInputStream());
            String basePath = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS).getAbsolutePath();
            destFile = new File(basePath + File.separator + sFile.getName());
            OutputStream out = new FileOutputStream(destFile);

            // Copy the bits from Instream to Outstream
            byte[] buf = new byte[1024];
            int len;
            long totalSize = sFile.length();
            long downloadedSize = 0;

            while ((len = inBuf.read(buf)) > 0) {
              out.write(buf, 0, len);
              downloadedSize += len;
              WritableMap params = Arguments.createMap();
              params.putString("fileName", sFile.getName() + "");
              params.putString("totalSize", totalSize + "");
              params.putString("downloadedSize", downloadedSize + "");
              sendEvent(reactContext, "SMBDownloadProgress", params);


            }
            inBuf.close();
            out.close();
            statusParams.putBoolean("success", true);
            statusParams.putString("errorMessage", "");
          }
        } catch (Exception e) {
          // Output the stack trace.
          e.printStackTrace();
          statusParams.putBoolean("success", false);
          statusParams.putString("errorMessage", "exception error: " + e.getMessage());
        }

          sendEvent(reactContext, "SMBDownloadResult", statusParams);
        }
      });
    }
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
