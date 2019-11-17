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
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.OutputStream;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
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

  Map<String, NtlmPasswordAuthentication> authenticationPool = new HashMap<>();
  Map<String, String> serverURLPool = new HashMap<>();

  private String serverURL = "smb://server_ip:server_port/shared_folder";

  private static LinkedBlockingQueue<Runnable> extraTaskQueue = new LinkedBlockingQueue<>();
  private static ThreadPoolExecutor extraThreadPool = new ThreadPoolExecutor(2, 10, 5000, TimeUnit.MILLISECONDS, extraTaskQueue);
  private static LinkedBlockingQueue<Runnable> downloadTaskQueue = new LinkedBlockingQueue<>();
  private static ThreadPoolExecutor downloadThreadPool = new ThreadPoolExecutor(2, 10, 5000, TimeUnit.MILLISECONDS, downloadTaskQueue);
  private static LinkedBlockingQueue<Runnable> uploadTaskQueue = new LinkedBlockingQueue<>();
  private static ThreadPoolExecutor uploadThreadPool = new ThreadPoolExecutor(2, 10, 5000, TimeUnit.MILLISECONDS, uploadTaskQueue);


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
      return false;
//      if (ActivityCompat.shouldShowRequestPermissionRationale(getCurrentActivity(), permission)) {
//        Toast.makeText(getCurrentActivity(), "Allow external storage reading", Toast.LENGTH_SHORT).show();
//        return false;
//      } else {
//        ActivityCompat.requestPermissions(getCurrentActivity(), new String[]{permission}, PERMISSIONS_REQUEST_CODE);
//        return true;
//      }
    }
    return true;
  }

  private boolean checkWriteExternalStoragePermissions() {
    String permission = Manifest.permission.WRITE_EXTERNAL_STORAGE;

    if (ContextCompat.checkSelfPermission(getCurrentActivity(), permission) != PackageManager.PERMISSION_GRANTED) {
      return false;
//      if (ActivityCompat.shouldShowRequestPermissionRationale(getCurrentActivity(), permission)) {
//        Toast.makeText(getCurrentActivity(), "Allow external storage Writing", Toast.LENGTH_SHORT).show();
//        return false;
//      } else {
//        ActivityCompat.requestPermissions(getCurrentActivity(), new String[]{permission}, PERMISSIONS_REQUEST_CODE);
//        return true;
//      }
    }
    return true;
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
  public void init(
          final String clientId,
          final ReadableMap options,
          final Callback callback
  ) {

    extraThreadPool.execute(new Runnable() {
      @Override
      public void run() {
        WritableMap params = Arguments.createMap();
        params.putString("name", "init");
        params.putString("clientId", clientId);

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
          params.putBoolean("success", false);
          params.putString("errorCode", "1001");
          params.putString("message", "invalid options structure: " + e.getMessage());
          callback.invoke(params);
          return;
        }


        try {
          if (!TextUtils.isEmpty(username) && !TextUtils.isEmpty(password)) {
            authentication = new NtlmPasswordAuthentication(workGroup, username, password);
            authenticationPool.put(clientId, authentication);

          }
        } catch (Exception e) {
          // NtlmPasswordAuthentication creation error
          e.printStackTrace();
          params.putBoolean("success", false);
          params.putString("errorCode", "1002");
          params.putString("message", "exception in initializing authentication: " + e.getMessage());
          callback.invoke(params);
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
            serverURLPool.put(clientId, server_url);
            serverURL = server_url;
            params.putBoolean("success", true);
            params.putString("errorCode", "0000");
            params.putString("serverURL", server_url);
            callback.invoke(params);
            return;
          }
        } catch (Exception e) {
          // server url initializing error
          e.printStackTrace();
          params.putBoolean("success", false);
          params.putString("errorCode", "1003");
          params.putString("message", "exception in initializing server url: " + e.getMessage());
          callback.invoke(params);
          return;
        }
      }


    });
  }

  @ReactMethod
  public void testConnection(
          final String clientId,
          final Callback callback
  ) {
    extraThreadPool.execute(new Runnable() {
      @Override
      public void run() {
        WritableMap params = Arguments.createMap();
        params.putString("name", "testConnection");
        params.putString("clientId", clientId);

        try {
          SmbFile sFile;
          NtlmPasswordAuthentication authentication = authenticationPool.get(clientId);
          String serverURL = serverURLPool.get(clientId);
          params.putString("serverURL", serverURL);
          if (authentication != null) {
            sFile = new SmbFile(serverURL, authentication);
          } else {
            sFile = new SmbFile(serverURL);
          }

          if (sFile.canRead()) {
            params.putBoolean("success", true);
            params.putString("errorCode", "0000");
            params.putString("message", "server [" + serverURL + "] can accessible.");
          } else {
            params.putBoolean("success", false);
            params.putString("errorCode", "1004");
            params.putString("message", "server [" + serverURL + "] not accessible.");
          }


        } catch (Exception e) {
          // Output the stack trace.
          e.printStackTrace();
          params.putBoolean("success", false);
          params.putString("errorCode", "0101");
          params.putString("message", "exception error: " + e.getMessage());
        }
        //sendEvent(reactContext, "SMBTestConnection", params);
        callback.invoke(params);

      }
    });
  }

  @ReactMethod
  public void list(
          final String clientId,
          @Nullable final String path,
          final Callback callback
  ) {
    extraThreadPool.execute(new Runnable() {
      @Override
      public void run() {
        WritableMap params = Arguments.createMap();
        params.putString("name", "list");
        params.putString("clientId", clientId);
        try {
          String destinationPath = "/";
          if (path != null && !TextUtils.isEmpty(path)) {
            destinationPath = "/" + path + destinationPath;
          }
          SmbFile sFile;
          NtlmPasswordAuthentication authentication = authenticationPool.get(clientId);
          String serverURL = serverURLPool.get(clientId);
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

              }

              params.putBoolean("success", true);
              params.putString("errorCode", "0000");
              params.putString("message", "path [" + path + "] list successfully.");
              params.putArray("list", list);
            } else {
              params.putBoolean("success", false);
              params.putString("message", "path [" + path + "] is not a directory.");
            }
          } else {
            params.putBoolean("success", false);
            params.putString("errorCode", "1005");
            params.putString("message", "can not read [" + path + "] directory.");
          }
        } catch (Exception e) {
          // Output the stack trace.
          e.printStackTrace();
          params.putBoolean("success", false);
          params.putString("errorCode", "0101");
          params.putString("message", "exception error: " + e.getMessage());
        }
        callback.invoke(params);
      }
    });
  }

  @ReactMethod
  public void download(
          @Nullable final String path,
          final String fileName
          ) {

    downloadThreadPool.execute(new Runnable() {
      @Override
      public void run() {

        WritableMap statusParams = Arguments.createMap();
        statusParams.putString("fileName", fileName + "");
        if (checkWriteExternalStoragePermissions()) {

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
              statusParams.putString("message", " [destinationPath] is a directory!!");
            } else if (!sFile.exists()) {
              statusParams.putBoolean("success", false);
              statusParams.putString("message", " [destinationPath] is not exist directory!!");
            } else if (!sFile.canRead()) {
              statusParams.putBoolean("success", false);
              statusParams.putString("message", " no permission  to read [destinationPath]!!");
            } else {
              BufferedInputStream inBuf = new BufferedInputStream(sFile.getInputStream());
              String basePath = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS).getAbsolutePath();
              destFile = new File(basePath + File.separator + sFile.getName());
              OutputStream out = new FileOutputStream(destFile);

              // Copy the bits from Instream to Outstream
              byte[] buf = new byte[100 * 1024];
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
              statusParams.putString("message", "");
            }
          } catch (Exception e) {
            // Output the stack trace.
            e.printStackTrace();
            statusParams.putBoolean("success", false);
            statusParams.putString("message", "download exception error: " + e.getMessage());
          }
        }else{
          statusParams.putBoolean("success", false);
          statusParams.putString("message", "no permission to access device storage!!! ");
        }
        sendEvent(reactContext, "SMBDownloadResult", statusParams);
      }
    });

  }

  @ReactMethod
  public void upload(
          @Nullable final String destinationPath,
          @Nullable final String sourcePath,
          final String sourceFileName
          ) {
    uploadThreadPool.execute(new Runnable() {
      @Override
      public void run() {
        WritableMap statusParams = Arguments.createMap();
        statusParams.putString("fileName", sourceFileName + "");
        if (checkReadExternalStoragePermissions()) {
          try {
            String basePath = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS).getAbsolutePath();
            String sourcePathWithSeparator = "";
            if (sourcePath != null && !TextUtils.isEmpty(sourcePath)) {
              sourcePathWithSeparator = sourcePath + File.separator;
            }
            File sourceFile = new File(basePath + File.separator + sourcePathWithSeparator + sourceFileName);
            SmbFile sFile = null;
            if (sourceFile.isDirectory()) {
              statusParams.putBoolean("success", false);
              statusParams.putString("message", " [sourcePath] is a directory!!");

            } else if (!sourceFile.exists()) {
              statusParams.putBoolean("success", false);
              statusParams.putString("message", " [sourcePath] is not exist!!");
            } else if (!sourceFile.canRead()) {
              statusParams.putBoolean("success", false);
              statusParams.putString("message", " no permission  to read [sourcePath]!!");
            } else {
              SmbFile sFilePath;
              String destinationPathWithSeparator = "/";
              if (destinationPath != null && !TextUtils.isEmpty(destinationPath)) {
                destinationPathWithSeparator += "/" + destinationPath;
              }
              if (sourceFileName != null && !TextUtils.isEmpty(sourceFileName)) {
                destinationPathWithSeparator += "/" + sourceFileName;
              }

              if (authentication != null) {
                sFile = new SmbFile(serverURL + destinationPathWithSeparator, authentication);
                sFilePath = new SmbFile(sFile.getParent(), authentication);

              } else {
                sFile = new SmbFile(serverURL + destinationPathWithSeparator);
                sFilePath = new SmbFile(sFile.getParent());

              }

              if (!sFilePath.exists()) sFilePath.mkdirs();

              BufferedInputStream inBuf = new BufferedInputStream(new FileInputStream(sourceFile));
              final SmbFileOutputStream smbFileOutputStream = new SmbFileOutputStream(sFile);
              //final byte[] buf = new byte[16 * 1024 * 1024];
              final byte[] buf = new byte[100 * 1024];
              int len;
              long totalSize = sourceFile.length();
              long uploadedSize = 0;
              while ((len = inBuf.read(buf)) > 0) {
                smbFileOutputStream.write(buf, 0, len);
                //todo: why write twice? I comment second write!!!!!
                //smbFileOutputStream.write(buf, 0, len);
                uploadedSize += len;
                WritableMap params = Arguments.createMap();
                params.putString("fileName", sourceFile.getName() + "");
                params.putString("totalSize", totalSize + "");
                params.putString("uploadedSize", uploadedSize + "");
                sendEvent(reactContext, "SMBUploadProgress", params);
              }
              inBuf.close();
              smbFileOutputStream.close();
              if (sFile != null && sFile.exists()) {
                statusParams.putBoolean("success", true);
                statusParams.putString("message", "successfully upload[" + sFile.getPath() + "]");
              } else {
                statusParams.putBoolean("success", false);
                statusParams.putString("message", "file not exist in server after upload[" + sFile.getPath() + "]!!!!");
              }

            }
          } catch (Exception e) {
            // Output the stack trace.
            e.printStackTrace();
            statusParams.putBoolean("success", false);
            statusParams.putString("message", "upload exception error: " + e.getMessage());
          }
        }else {
          statusParams.putBoolean("success", false);
          statusParams.putString("message", "no permission to access device storage!!! ");
        }
        sendEvent(reactContext, "SMBUploadResult", statusParams);
      }
    });

  }

  @ReactMethod
  public void rename(
          @Nullable final String path,
          final String oldFileName,
          final String newFileName
          ) {
      extraThreadPool.execute(new Runnable() {
        @Override
        public void run() {
          WritableMap statusParams = Arguments.createMap();
          statusParams.putString("oldFileName", oldFileName + "");
          statusParams.putString("newFileName", newFileName + "");
          try {
            String oldPath = "/";
            if (path != null && !TextUtils.isEmpty(path)) {
              oldPath = "/" + path + oldPath;
            }
            if (oldFileName != null && !TextUtils.isEmpty(oldFileName)) {
              oldPath = oldPath + oldFileName;
            }
            SmbFile oldSmbFile;
            if (authentication != null) {
              oldSmbFile = new SmbFile(serverURL + oldPath, authentication);
            } else {
              oldSmbFile = new SmbFile(serverURL + oldPath);
            }

            if (oldSmbFile.isDirectory()) {
              statusParams.putBoolean("success", false);
              statusParams.putString("message", " file is a directory [sourcePath]!!");

            } else if (!oldSmbFile.exists()) {
              statusParams.putBoolean("success", false);
              statusParams.putString("message", " file is not exist [sourcePath]!!");
            } else if (!oldSmbFile.canRead()) {
              statusParams.putBoolean("success", false);
              statusParams.putString("message", " no permission  to read [sourcePath]!!");
            } else if (!oldSmbFile.canWrite()) {
              statusParams.putBoolean("success", false);
              statusParams.putString("message", " no permission  to write [sourcePath]!!");
            } else {
              String newPath = "/";
              if (path != null && !TextUtils.isEmpty(path)) {
                newPath = "/" + path + newPath;
              }
              if (newFileName != null && !TextUtils.isEmpty(newFileName)) {
                newPath = newPath + newFileName;
              }
              SmbFile newSmbFile;
              if (authentication != null) {
                newSmbFile = new SmbFile(serverURL + newPath, authentication);
              } else {
                newSmbFile = new SmbFile(serverURL + newPath);
              }
              if(newSmbFile.exists()){
                statusParams.putBoolean("success", false);
                statusParams.putString("message", "new file name ["+newSmbFile.getPath()+"] exist!!!!");
              }else {
                oldSmbFile.renameTo(newSmbFile);
                if (newSmbFile != null && newSmbFile.exists()) {
                  statusParams.putBoolean("success", true);
                  statusParams.putString("message", "successfully renamed[" + newSmbFile.getPath() + "]");
                } else {
                  statusParams.putBoolean("success", false);
                  statusParams.putString("message", "file not exist in server after rename[" + newSmbFile.getPath() + "]!!!!");
                }
              }
            }
          } catch (Exception e) {
            // Output the stack trace.
            e.printStackTrace();
            statusParams.putBoolean("success", false);
            statusParams.putString("message", "rename exception error: " + e.getMessage());
          }
          sendEvent(reactContext, "SMBRenameResult", statusParams);
        }
      });
  }

  @ReactMethod
  public void moveTo(
          @Nullable final String oldPath,
          @Nullable final String newPath,
          final String fileName
          ) {
      extraThreadPool.execute(new Runnable() {
        @Override
        public void run() {
          WritableMap statusParams = Arguments.createMap();
          statusParams.putString("fileName", fileName + "");
          try {
            String oldFullPath = "/";
            if (oldPath != null && !TextUtils.isEmpty(oldPath)) {
              oldFullPath = "/" + oldPath + oldFullPath;
            }
            if (fileName != null && !TextUtils.isEmpty(fileName)) {
              oldFullPath = oldFullPath + fileName;
            }
            SmbFile oldSmbFile;
            if (authentication != null) {
              oldSmbFile = new SmbFile(serverURL + oldFullPath, authentication);
            } else {
              oldSmbFile = new SmbFile(serverURL + oldFullPath);
            }

            if (oldSmbFile.isDirectory()) {
              statusParams.putBoolean("success", false);
              statusParams.putString("message", " file is a directory [sourcePath]!!");

            } else if (!oldSmbFile.exists()) {
              statusParams.putBoolean("success", false);
              statusParams.putString("message", " file is not exist [sourcePath]!!");
            } else if (!oldSmbFile.canRead()) {
              statusParams.putBoolean("success", false);
              statusParams.putString("message", " no permission  to read [sourcePath]!!");
            } else if (!oldSmbFile.canWrite()) {
              statusParams.putBoolean("success", false);
              statusParams.putString("message", " no permission  to write [sourcePath]!!");
            } else {
              String newFullPath = "/";
              if (newPath != null && !TextUtils.isEmpty(newPath)) {
                newFullPath = "/" + newPath + newFullPath;
              }
              if (fileName != null && !TextUtils.isEmpty(fileName)) {
                newFullPath = newFullPath + fileName;
              }
              SmbFile newSmbFile;
              SmbFile newSmbFilePath;
              if (authentication != null) {
                newSmbFile = new SmbFile(serverURL + newFullPath, authentication);
                newSmbFilePath = new SmbFile(newSmbFile.getParent(), authentication);
              } else {
                newSmbFile = new SmbFile(serverURL + newFullPath);
                newSmbFilePath = new SmbFile(newSmbFile.getParent(), authentication);
              }
              if(newSmbFile.exists()){
                statusParams.putBoolean("success", false);
                statusParams.putString("message", "file in destination path ["+newSmbFile.getPath()+"] exist!!!!");
              }else {

                if (!newSmbFilePath.exists()) newSmbFilePath.mkdirs();
                oldSmbFile.renameTo(newSmbFile);
                if (newSmbFile != null && newSmbFile.exists()) {
                  statusParams.putBoolean("success", true);
                  statusParams.putString("message", "successfully moved[" + newSmbFile.getPath() + "]");
                } else {
                  statusParams.putBoolean("success", false);
                  statusParams.putString("message", "file not exist in server after moving[" + newSmbFile.getPath() + "]!!!!");
                }
              }
            }
          } catch (Exception e) {
            // Output the stack trace.
            e.printStackTrace();
            statusParams.putBoolean("success", false);
            statusParams.putString("message", "move exception error: " + e.getMessage());
          }
          sendEvent(reactContext, "SMBMoveResult", statusParams);
        }
      });
  }

  @ReactMethod
  public void copyTo(
          @Nullable final String fromPath,
          @Nullable final String toPath,
          final String fileName
          ) {
      extraThreadPool.execute(new Runnable() {
        @Override
        public void run() {
          WritableMap statusParams = Arguments.createMap();
          statusParams.putString("fileName", fileName + "");
          try {
            String fromFullPath = "/";
            if (fromPath != null && !TextUtils.isEmpty(fromPath)) {
              fromFullPath = "/" + fromPath + fromFullPath;
            }
            if (fileName != null && !TextUtils.isEmpty(fileName)) {
              fromFullPath = fromFullPath + fileName;
            }
            SmbFile fromSmbFile;
            if (authentication != null) {
              fromSmbFile = new SmbFile(serverURL + fromFullPath, authentication);
            } else {
              fromSmbFile = new SmbFile(serverURL + fromFullPath);
            }

            if (fromSmbFile.isDirectory()) {
              statusParams.putBoolean("success", false);
              statusParams.putString("message", " file is a directory [sourcePath]!!");

            } else if (!fromSmbFile.exists()) {
              statusParams.putBoolean("success", false);
              statusParams.putString("message", " file is not exist [sourcePath]!!");
            } else if (!fromSmbFile.canRead()) {
              statusParams.putBoolean("success", false);
              statusParams.putString("message", " no permission  to read [sourcePath]!!");
            } else if (!fromSmbFile.canWrite()) {
              statusParams.putBoolean("success", false);
              statusParams.putString("message", " no permission  to write [sourcePath]!!");
            } else {
              String toFullPath = "/";
              if (toPath != null && !TextUtils.isEmpty(toPath)) {
                toFullPath = "/" + toPath + toFullPath;
              }
              if (fileName != null && !TextUtils.isEmpty(fileName)) {
                toFullPath = toFullPath + fileName;
              }
              SmbFile toSmbFile;
              SmbFile toSmbFilePath;
              if (authentication != null) {
                toSmbFile = new SmbFile(serverURL + toFullPath, authentication);
                toSmbFilePath = new SmbFile(toSmbFile.getParent(), authentication);
              } else {
                toSmbFile = new SmbFile(serverURL + toFullPath);
                toSmbFilePath = new SmbFile(toSmbFile.getParent(), authentication);
              }
              if(toSmbFile.exists()){
                statusParams.putBoolean("success", false);
                statusParams.putString("message", "file in destination path ["+toSmbFile.getPath()+"] exist!!!!");
              }else {
                if (!toSmbFilePath.exists()) toSmbFilePath.mkdirs();
                fromSmbFile.copyTo(toSmbFile);
                if (toSmbFile != null && toSmbFile.exists()) {
                  statusParams.putBoolean("success", true);
                  statusParams.putString("message", "successfully copy[" + toSmbFile.getPath() + "]");
                } else {
                  statusParams.putBoolean("success", false);
                  statusParams.putString("message", "file not exist in server after copy[" + toSmbFile.getPath() + "]!!!!");
                }
              }
            }
          } catch (Exception e) {
            // Output the stack trace.
            e.printStackTrace();
            statusParams.putBoolean("success", false);
            statusParams.putString("message", "copy exception error: " + e.getMessage());
          }
          sendEvent(reactContext, "SMBCopyResult", statusParams);
        }
      });
  }


  @ReactMethod
  public void makeDir(
          @Nullable final String newPath
          ) {
      extraThreadPool.execute(new Runnable() {
        @Override
        public void run() {
          WritableMap statusParams = Arguments.createMap();
          statusParams.putString("path", newPath + "");
          try {
            String fullPath = "/";
            if (newPath != null && !TextUtils.isEmpty(newPath)) {
              fullPath = "/" + newPath + fullPath;
            }
            SmbFile newSmbFile;
            if (authentication != null) {
              newSmbFile = new SmbFile(serverURL + fullPath, authentication);
            } else {
              newSmbFile = new SmbFile(serverURL + fullPath);
            }

            if (newSmbFile.isFile()) {
              statusParams.putBoolean("success", false);
              statusParams.putString("message", " can not create a file [sourcePath]!!");
            } else if (newSmbFile.exists()) {
              statusParams.putBoolean("success", false);
              statusParams.putString("message", " file already exist [sourcePath]!!");
            } else {
               newSmbFile.mkdirs();
                if (newSmbFile != null && newSmbFile.exists()) {
                  statusParams.putBoolean("success", true);
                  statusParams.putString("message", "directory successfully created[" + newSmbFile.getPath() + "]");
                } else {
                  statusParams.putBoolean("success", false);
                  statusParams.putString("message", "directory not exist in server after creation[" + newSmbFile.getPath() + "]!!!!");
                }

            }
          } catch (Exception e) {
            // Output the stack trace.
            e.printStackTrace();
            statusParams.putBoolean("success", false);
            statusParams.putString("message", "make directory exception error: " + e.getMessage());
          }
          sendEvent(reactContext, "SMBMakeDirResult", statusParams);
        }
      });
  }

  @ReactMethod
  public void delete(
          @Nullable final String targetPath
          ) {
      extraThreadPool.execute(new Runnable() {
        @Override
        public void run() {
          WritableMap statusParams = Arguments.createMap();
          statusParams.putString("path", targetPath + "");
          try {
            String fullTargetPath = "/";
            if (targetPath != null && !TextUtils.isEmpty(targetPath)) {
              fullTargetPath = "/" + targetPath + fullTargetPath;
            }
            SmbFile targetSmbFile;
            if (authentication != null) {
              targetSmbFile = new SmbFile(serverURL + fullTargetPath, authentication);
            } else {
              targetSmbFile = new SmbFile(serverURL + fullTargetPath);
            }

            if (!targetSmbFile.exists()) {
              statusParams.putBoolean("success", false);
              statusParams.putString("message", " can not find file or directory to delete [sourcePath]!!");
            } else if (!targetSmbFile.canWrite()) {
              statusParams.putBoolean("success", false);
              statusParams.putString("message", " no permission to delete file or directory [sourcePath]!!");
            } else {
              targetSmbFile.delete();
                if (!targetSmbFile.exists()) {
                  statusParams.putBoolean("success", true);
                  statusParams.putString("message", "directory or file successfully deleted[" + targetSmbFile.getPath() + "]");
                } else {
                  statusParams.putBoolean("success", false);
                  statusParams.putString("message", "directory or file not deleted in server [" + targetSmbFile.getPath() + "]!!!!");
                }

            }
          } catch (Exception e) {
            // Output the stack trace.
            e.printStackTrace();
            statusParams.putBoolean("success", false);
            statusParams.putString("message", "delete exception error: " + e.getMessage());
          }
          sendEvent(reactContext, "SMBDeleteResult", statusParams);
        }
      });
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
