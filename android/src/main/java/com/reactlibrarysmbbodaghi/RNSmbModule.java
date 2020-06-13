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
import com.hierynomus.msdtyp.AccessMask;
import com.hierynomus.msfscc.FileAttributes;
import com.hierynomus.msfscc.fileinformation.FileAllInformation;
import com.hierynomus.msfscc.fileinformation.FileIdBothDirectoryInformation;
import com.hierynomus.mssmb2.SMB2CreateDisposition;
import com.hierynomus.mssmb2.SMB2ShareAccess;
import com.hierynomus.protocol.commons.EnumWithValue;
import com.hierynomus.smbj.SMBClient;
import com.hierynomus.smbj.auth.AuthenticationContext;
import com.hierynomus.smbj.connection.Connection;
import com.hierynomus.smbj.session.Session;
import com.hierynomus.smbj.share.DiskShare;

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
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.EnumSet;
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

  Map<String, NtlmPasswordAuthentication> authenticationPoolSMB1 = new HashMap<>();
  Map<String, AuthenticationContext> authenticationPool = new HashMap<>();
  Map<String, Connection> connectionPool = new HashMap<>();
  Map<String, DiskShare> diskSharePool = new HashMap<>();
  Map<String, String> serverURLPool = new HashMap<>();
  Map<String, String> downloadPool = new HashMap<>();
  Map<String, List<String>> clientDownloadsPool = new HashMap<>();
  Map<String, String> uploadPool = new HashMap<>();
  Map<String, List<String>> clientUploadsPool = new HashMap<>();


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

  /**
   * only SMB 1 supported apis (used jcifs.smb)
   *
   */

  @ReactMethod
  public void SMB1Init(
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
            authenticationPoolSMB1.put(clientId, authentication);

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
  public void SMB1TestConnection(
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
          NtlmPasswordAuthentication authentication = authenticationPoolSMB1.get(clientId);
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
        callback.invoke(params);

      }
    });
  }

  @ReactMethod
  public void SMB1List(
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
          NtlmPasswordAuthentication authentication = authenticationPoolSMB1.get(clientId);
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
  public void SMB1Download(
          final String clientId,
          final String downloadId,
          @Nullable final String fromPath,
          @Nullable final String toPath,
          final String fileName,
          final Callback callback
  ) {

    downloadThreadPool.execute(new Runnable() {
      @Override
      public void run() {

        SmbFile srcFile = null;
        File destFile = null;
        boolean isDownloadInitialized = false;

        WritableMap statusParams = Arguments.createMap();
        statusParams.putString("name", "download");
        statusParams.putString("clientId", clientId);
        statusParams.putString("downloadId", downloadId);
        statusParams.putString("fileName", fileName + "");
        statusParams.putString("fromPath", fromPath + "");
        statusParams.putString("toPath", toPath + "");
        try {
          if (checkWriteExternalStoragePermissions()) {

            String destinationPath = "/";
            if (fromPath != null && !TextUtils.isEmpty(fromPath)) {
              destinationPath = "/" + fromPath + destinationPath;
            }
            if (fileName != null && !TextUtils.isEmpty(fileName)) {
              destinationPath = destinationPath + fileName;
            }
            //SmbFile srcFile;
            NtlmPasswordAuthentication authentication = authenticationPoolSMB1.get(clientId);
            String serverURL = serverURLPool.get(clientId);
            if (TextUtils.isEmpty(serverURL)) {
                statusParams.putBoolean("success", false);
                statusParams.putString("errorCode", "1111");
                statusParams.putString("message", " serverURL is null [for client "+clientId+"] !!");
                callback.invoke(statusParams);
                return;
            }
            if (authentication != null) {
              srcFile = new SmbFile(serverURL + destinationPath, authentication);
            } else {
              srcFile = new SmbFile(serverURL + destinationPath);
            }


            if (srcFile.isDirectory()) {
              statusParams.putBoolean("success", false);
              statusParams.putString("errorCode", "1111");
              statusParams.putString("message", " [" + destinationPath + "] is a directory!!");
              callback.invoke(statusParams);
              return;
            }
            if (!srcFile.exists()) {
              statusParams.putBoolean("success", false);
              statusParams.putString("errorCode", "1111");
              statusParams.putString("message", " [" + destinationPath + "] is not exist!!");
              callback.invoke(statusParams);
              return;
            }
            if (!srcFile.canRead()) {
              statusParams.putBoolean("success", false);
              statusParams.putString("errorCode", "1111");
              statusParams.putString("message", "no permission  to read [" + destinationPath + "]!!");
              callback.invoke(statusParams);
              return;
            }

            //source file initialized successfully, now initializing destination file

            String basePath;
            if (toPath == null || TextUtils.isEmpty(toPath)) {
              basePath = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS).getAbsolutePath();
            } else {
              basePath = toPath;
            }

            //File destFile;
            destFile = new File(basePath + File.separator + srcFile.getName());

            //destination file initialized successfully

            statusParams.putString("destPath", "" + destFile.getAbsolutePath());
            statusParams.putBoolean("success", true);
            statusParams.putString("errorCode", "0000");
            statusParams.putString("message", "download initialized successfully!!! ");

            isDownloadInitialized = true;
          } else {
            statusParams.putBoolean("success", false);
            statusParams.putString("errorCode", "1111");
            statusParams.putString("message", "no permission to access device storage!!! ");
          }
        } catch (Exception e) {
          // Output the stack trace.
          e.printStackTrace();
          statusParams.putBoolean("success", false);
          statusParams.putString("errorCode", "0101");
          statusParams.putString("message", "download exception error: " + e.getMessage());
        }
        callback.invoke(statusParams);

        if (isDownloadInitialized && srcFile != null && destFile != null) {
          try {
            BufferedInputStream inBuf = new BufferedInputStream(srcFile.getInputStream());
            OutputStream out = new FileOutputStream(destFile);

            // write the bits from Instream to Outstream
            //byte[] buf = new byte[16 * 1024 * 1024];
            byte[] buf = new byte[100 * 1024];
            int len;
            long totalSize = srcFile.length();
            long downloadedSize = 0;

            List<String> downloadIds = clientDownloadsPool.remove(clientId);
            if (downloadIds == null || downloadIds.isEmpty()) downloadIds = new ArrayList<String>();
            if (downloadIds.indexOf(downloadId) == -1) downloadIds.add(downloadId);
            clientDownloadsPool.put(clientId, downloadIds);

            downloadPool.put(downloadId, "inProgress");
            String downloadStatus = "inProgress";

            while ((len = inBuf.read(buf)) > 0) {
              downloadStatus = downloadPool.get(downloadId);
              if (downloadStatus == null) downloadStatus = "cancel";
//              if (downloadStatus == "cancel") {
//                break;
//              }
              out.write(buf, 0, len);
              downloadedSize += len;

              boolean isCompleted = totalSize == downloadedSize;
              String message = "downloading";
              String status = "downloading";

              if (isCompleted) {
                message = "download completed successfully.";
                status = "completed";
              } else if (downloadStatus == "cancel") {
                message = "download canceled";
                status = "canceled";
              }

              WritableMap params = Arguments.createMap();
              params.putString("name", "downloadProgress");
              params.putBoolean("success", true);
              params.putBoolean("completed", isCompleted);
              params.putString("errorCode", "0000");
              params.putString("message", message);
              params.putString("status", status);
              params.putString("clientId", clientId);
              params.putString("downloadId", downloadId);
              params.putString("fileName", srcFile.getName() + "");
              params.putString("fromPath", fromPath + "");
              params.putString("toPath", toPath + "");
              params.putString("srcPath", "" + srcFile.getPath());
              params.putString("destPath", "" + destFile.getAbsolutePath());
              params.putString("totalSize", totalSize + "");
              params.putString("downloadedSize", downloadedSize + "");
              sendEvent(reactContext, "SMBDownloadProgress", params);

              if (downloadStatus == "cancel") {
                break;
              }
            }
            inBuf.close();
            out.close();


            if (downloadStatus == "cancel") {
              destFile.delete();
            }

          } catch (Exception e) {
            // Output the stack trace.
            e.printStackTrace();
            WritableMap params = Arguments.createMap();
            params.putString("name", "downloadProgress");
            params.putString("clientId", clientId);
            params.putString("downloadId", downloadId);
            params.putString("fileName", srcFile.getName() + "");
            params.putString("fromPath", fromPath + "");
            params.putString("toPath", toPath + "");
            params.putString("srcPath", "" + srcFile.getPath());
            params.putString("destPath", "" + destFile.getAbsolutePath());
            params.putBoolean("success", false);
            params.putString("errorCode", "0101");
            params.putString("message", "download progress exception error: " + e.getMessage());
            sendEvent(reactContext, "SMBDownloadProgress", params);
          }

          downloadPool.remove(downloadId);
          List<String> downloadIds = clientDownloadsPool.remove(clientId);
          if (downloadIds != null && !downloadIds.isEmpty()) {
            downloadIds.remove(downloadId);
            clientDownloadsPool.put(clientId, downloadIds);
          }
        }
      }
    });

  }

  @ReactMethod
  public void SMB1CancelDownload(
          final String clientId,
          final String downloadId
  ) {
    try {
      downloadPool.put(downloadId, "cancel");
    } catch (Exception e) {
      // Output the stack trace.
      e.printStackTrace();
    }
  }

  @ReactMethod
  public void SMB1Upload(
          final String clientId,
          final String uploadId,
          @Nullable final String fromPath,
          @Nullable final String toPath,
          final String fileName,
          final Callback callback
  ) {
    uploadThreadPool.execute(new Runnable() {
      @Override
      public void run() {

        File srcFile = null;
        SmbFile destFile = null;
        boolean isUploadInitialized = false;

        WritableMap statusParams = Arguments.createMap();
        statusParams.putString("name", "upload");
        statusParams.putString("clientId", clientId);
        statusParams.putString("uploadId", uploadId);
        statusParams.putString("fileName", fileName + "");
        statusParams.putString("fromPath", fromPath + "");
        statusParams.putString("toPath", toPath + "");
        try {
          if (checkReadExternalStoragePermissions()) {
            String basePath = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS).getAbsolutePath();
            String sourcePathWithSeparator = "";
            if (fromPath != null && !TextUtils.isEmpty(fromPath)) {
              sourcePathWithSeparator = fromPath + File.separator;
            }
            srcFile = new File(basePath + File.separator + sourcePathWithSeparator + fileName);

            if (srcFile.isDirectory()) {
              statusParams.putBoolean("success", false);
              statusParams.putString("errorCode", "1111");
              statusParams.putString("message", "[" + sourcePathWithSeparator + fileName + "] is a directory!!");
              callback.invoke(statusParams);
              return;
            }
            if (!srcFile.exists()) {
              statusParams.putBoolean("success", false);
              statusParams.putString("errorCode", "1111");
              statusParams.putString("message", "[" + sourcePathWithSeparator + fileName + "] is not exist!!");
              callback.invoke(statusParams);
              return;
            }
            if (!srcFile.canRead()) {
              statusParams.putBoolean("success", false);
              statusParams.putString("errorCode", "1111");
              statusParams.putString("message", " no permission  to read [" + sourcePathWithSeparator + fileName + "]!!");
              callback.invoke(statusParams);
              return;
            }
            //source file initialized successfully, now initializing destination file

            String destinationPathWithSeparator = "/";
            if (toPath != null && !TextUtils.isEmpty(toPath)) {
              destinationPathWithSeparator += "/" + toPath;
            }
            if (fileName != null && !TextUtils.isEmpty(fileName)) {
              destinationPathWithSeparator += "/" + fileName;
            }
            SmbFile destFilePath;
            NtlmPasswordAuthentication authentication = authenticationPoolSMB1.get(clientId);
            String serverURL = serverURLPool.get(clientId);
            if (authentication != null) {
              destFile = new SmbFile(serverURL + destinationPathWithSeparator, authentication);
              destFilePath = new SmbFile(destFile.getParent(), authentication);
            } else {
              destFile = new SmbFile(serverURL + destinationPathWithSeparator);
              destFilePath = new SmbFile(destFile.getParent());
            }
            if (!destFilePath.exists()) destFilePath.mkdirs();
            //destination file initialized successfully

            statusParams.putString("destPath", "" + destFile.getPath());
            statusParams.putBoolean("success", true);
            statusParams.putString("errorCode", "0000");
            statusParams.putString("message", "upload initialized successfully!!! ");

            isUploadInitialized = true;

          } else {
            statusParams.putBoolean("success", false);
            statusParams.putString("errorCode", "1111");
            statusParams.putString("message", "no permission to access device storage!!! ");
          }
        } catch (Exception e) {
          // Output the stack trace.
          e.printStackTrace();
          statusParams.putBoolean("success", false);
          statusParams.putString("errorCode", "0101");
          statusParams.putString("message", "upload exception error: " + e.getMessage());
        }

        callback.invoke(statusParams);


        if (isUploadInitialized && srcFile != null && destFile != null) {
          try {
            BufferedInputStream inBuf = new BufferedInputStream(new FileInputStream(srcFile));
            final SmbFileOutputStream smbFileOutputStream = new SmbFileOutputStream(destFile);

            // write the bits from Instream to Outstream
            //byte[] buf = new byte[16 * 1024 * 1024];
            byte[] buf = new byte[100 * 1024];
            int len;
            long totalSize = srcFile.length();
            long uploadedSize = 0;

            List<String> uploadIds = clientUploadsPool.remove(clientId);
            if (uploadIds == null || uploadIds.isEmpty()) uploadIds = new ArrayList<String>();
            if (uploadIds.indexOf(uploadId) == -1) uploadIds.add(uploadId);
            clientUploadsPool.put(clientId, uploadIds);

            uploadPool.put(uploadId, "inProgress");
            String uploadStatus = "inProgress";

            while ((len = inBuf.read(buf)) > 0) {
              uploadStatus = uploadPool.get(uploadId);
              if (uploadStatus == null) uploadStatus = "cancel";
//              if (uploadStatus == "cancel") {
//                break;
//              }
              smbFileOutputStream.write(buf, 0, len);
              //todo: why write twice? I comment second write!!!!!
              //smbFileOutputStream.write(buf, 0, len);
              uploadedSize += len;

              boolean isCompleted = totalSize == uploadedSize;
              String message = "uploading";
              String status = "uploading";

              if (isCompleted) {
                message = "upload completed successfully.";
                status = "completed";
              } else if (uploadStatus == "cancel") {
                message = "upload canceled";
                status = "canceled";
              }

              WritableMap params = Arguments.createMap();
              params.putString("name", "uploadProgress");
              params.putBoolean("success", true);
              params.putBoolean("completed", isCompleted);
              params.putString("errorCode", "0000");
              params.putString("message", message);
              params.putString("status", status);
              params.putString("clientId", clientId);
              params.putString("uploadId", uploadId);
              params.putString("fileName", srcFile.getName() + "");
              params.putString("fromPath", fromPath + "");
              params.putString("toPath", toPath + "");
              params.putString("srcPath", "" + srcFile.getAbsolutePath());
              params.putString("destPath", "" + destFile.getPath());
              params.putString("totalSize", totalSize + "");
              params.putString("uploadedSize", uploadedSize + "");
              sendEvent(reactContext, "SMBUploadProgress", params);

              if (uploadStatus == "cancel") {
                break;
              }
            }
            inBuf.close();
            smbFileOutputStream.close();

            if (uploadStatus == "cancel") {
              destFile.delete();
            }

          } catch (Exception e) {
            // Output the stack trace.
            e.printStackTrace();
            WritableMap params = Arguments.createMap();
            params.putString("name", "uploadProgress");
            params.putString("clientId", clientId);
            params.putString("uploadId", uploadId);
            params.putString("fileName", srcFile.getName() + "");
            params.putString("fromPath", fromPath + "");
            params.putString("toPath", toPath + "");
            params.putString("srcPath", "" + srcFile.getAbsolutePath());
            params.putString("destPath", "" + destFile.getPath());
            params.putBoolean("success", false);
            params.putString("errorCode", "0101");
            params.putString("message", "upload progress exception error: " + e.getMessage());
            sendEvent(reactContext, "SMBUploadProgress", params);
          }
          uploadPool.remove(uploadId);

          List<String> uploadIds = clientUploadsPool.remove(clientId);

          if (uploadIds != null && !uploadIds.isEmpty()) {
            uploadIds.remove(uploadId);
            clientUploadsPool.put(clientId, uploadIds);
          }
        }
      }
    });
  }

  @ReactMethod
  public void SMB1CancelUpload(
          final String clientId,
          final String uploadId
  ) {
    try {
      uploadPool.put(uploadId, "cancel");
    } catch (Exception e) {
      // Output the stack trace.
      e.printStackTrace();
    }
  }

  @ReactMethod
  public void SMB1Rename(
          final String clientId,
          @Nullable final String path,
          final String oldFileName,
          final String newFileName,
          final Callback callback
  ) {
    extraThreadPool.execute(new Runnable() {
      @Override
      public void run() {
        WritableMap statusParams = Arguments.createMap();
        statusParams.putString("name", "rename");
        statusParams.putString("clientId", clientId);
        statusParams.putString("path", path + "");
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
          NtlmPasswordAuthentication authentication = authenticationPoolSMB1.get(clientId);
          String serverURL = serverURLPool.get(clientId);
          if (authentication != null) {
            oldSmbFile = new SmbFile(serverURL + oldPath, authentication);
          } else {
            oldSmbFile = new SmbFile(serverURL + oldPath);
          }

//          if (oldSmbFile.isDirectory()) {
//            statusParams.putBoolean("success", false);
//            statusParams.putString("message", " file is a directory [sourcePath]!!");
//
//          } else
          if (!oldSmbFile.exists()) {
            statusParams.putBoolean("success", false);
            statusParams.putString("errorCode", "1111");
            statusParams.putString("message", " file is not exist [sourcePath]!!");
          } else if (!oldSmbFile.canRead()) {
            statusParams.putBoolean("success", false);
            statusParams.putString("errorCode", "1111");
            statusParams.putString("message", " no permission  to read [sourcePath]!!");
          } else if (!oldSmbFile.canWrite()) {
            statusParams.putBoolean("success", false);
            statusParams.putString("errorCode", "1111");
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
            if (newSmbFile.exists()) {
              statusParams.putBoolean("success", false);
              statusParams.putString("errorCode", "1111");
              statusParams.putString("message", "new file name [" + newSmbFile.getPath() + "] exist!!!!");
            } else {
              oldSmbFile.renameTo(newSmbFile);
              if (newSmbFile != null && newSmbFile.exists()) {
                statusParams.putBoolean("success", true);
                statusParams.putString("errorCode", "0000");
                statusParams.putString("message", "successfully renamed[" + newSmbFile.getPath() + "]");
              } else {
                statusParams.putBoolean("success", false);
                statusParams.putString("errorCode", "1111");
                statusParams.putString("message", "file not exist in server after rename[" + newSmbFile.getPath() + "]!!!!");
              }
            }
          }
        } catch (Exception e) {
          // Output the stack trace.
          e.printStackTrace();
          statusParams.putBoolean("success", false);
          statusParams.putString("errorCode", "0101");
          statusParams.putString("message", "rename exception error: " + e.getMessage());
        }
        callback.invoke(statusParams);
      }
    });
  }

  @ReactMethod
  public void SMB1MoveTo(
          final String clientId,
          @Nullable final String fromPath,
          @Nullable final String toPath,
          final String fileName,
          final Callback callback
  ) {
    extraThreadPool.execute(new Runnable() {
      @Override
      public void run() {
        WritableMap statusParams = Arguments.createMap();
        statusParams.putString("name", "moveTo");
        statusParams.putString("clientId", clientId);
        statusParams.putString("fromPath", fromPath + "");
        statusParams.putString("toPath", toPath + "");
        statusParams.putString("fileName", fileName + "");
        try {
          String oldFullPath = "/";
          if (fromPath != null && !TextUtils.isEmpty(fromPath)) {
            oldFullPath = "/" + fromPath + oldFullPath;
          }
          if (fileName != null && !TextUtils.isEmpty(fileName)) {
            oldFullPath = oldFullPath + fileName;
          }
          SmbFile oldSmbFile;
          NtlmPasswordAuthentication authentication = authenticationPoolSMB1.get(clientId);
          String serverURL = serverURLPool.get(clientId);
          if (authentication != null) {
            oldSmbFile = new SmbFile(serverURL + oldFullPath, authentication);
          } else {
            oldSmbFile = new SmbFile(serverURL + oldFullPath);
          }

          if (oldSmbFile.isDirectory()) {
            statusParams.putBoolean("success", false);
            statusParams.putString("errorCode", "1111");
            statusParams.putString("message", " file is a directory [sourcePath]!!");
          } else if (!oldSmbFile.exists()) {
            statusParams.putBoolean("success", false);
            statusParams.putString("errorCode", "1111");
            statusParams.putString("message", " file is not exist [sourcePath]!!");
          } else if (!oldSmbFile.canRead()) {
            statusParams.putBoolean("success", false);
            statusParams.putString("errorCode", "1111");
            statusParams.putString("message", " no permission  to read [sourcePath]!!");
          } else if (!oldSmbFile.canWrite()) {
            statusParams.putBoolean("success", false);
            statusParams.putString("errorCode", "1111");
            statusParams.putString("message", " no permission  to write [sourcePath]!!");
          } else {
            String newFullPath = "/";
            if (toPath != null && !TextUtils.isEmpty(toPath)) {
              newFullPath = "/" + toPath + newFullPath;
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
            if (newSmbFile.exists()) {
              statusParams.putBoolean("success", false);
              statusParams.putString("errorCode", "1111");
              statusParams.putString("message", "file in destination path [" + newSmbFile.getPath() + "] exist!!!!");
            } else {

              if (!newSmbFilePath.exists()) newSmbFilePath.mkdirs();
              oldSmbFile.renameTo(newSmbFile);
              if (newSmbFile != null && newSmbFile.exists()) {
                statusParams.putBoolean("success", true);
                statusParams.putString("errorCode", "0000");
                statusParams.putString("message", "successfully moved[" + newSmbFile.getPath() + "]");
              } else {
                statusParams.putBoolean("success", false);
                statusParams.putString("errorCode", "1111");
                statusParams.putString("message", "file not exist in server after moving[" + newSmbFile.getPath() + "]!!!!");
              }
            }
          }
        } catch (Exception e) {
          // Output the stack trace.
          e.printStackTrace();
          statusParams.putBoolean("success", false);
          statusParams.putString("errorCode", "0101");
          statusParams.putString("message", "move exception error: " + e.getMessage());
        }
        callback.invoke(statusParams);
      }
    });
  }

  @ReactMethod
  public void SMB1CopyTo(
          final String clientId,
          @Nullable final String fromPath,
          @Nullable final String toPath,
          final String fileName,
          final Callback callback
  ) {
    extraThreadPool.execute(new Runnable() {
      @Override
      public void run() {
        WritableMap statusParams = Arguments.createMap();
        statusParams.putString("name", "copyTo");
        statusParams.putString("clientId", clientId);
        statusParams.putString("fromPath", fromPath + "");
        statusParams.putString("toPath", toPath + "");
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
          NtlmPasswordAuthentication authentication = authenticationPoolSMB1.get(clientId);
          String serverURL = serverURLPool.get(clientId);
          if (authentication != null) {
            fromSmbFile = new SmbFile(serverURL + fromFullPath, authentication);
          } else {
            fromSmbFile = new SmbFile(serverURL + fromFullPath);
          }

          if (fromSmbFile.isDirectory()) {
            statusParams.putBoolean("success", false);
            statusParams.putString("errorCode", "1111");
            statusParams.putString("message", " file is a directory [sourcePath]!!");

          } else if (!fromSmbFile.exists()) {
            statusParams.putBoolean("success", false);
            statusParams.putString("errorCode", "1111");
            statusParams.putString("message", " file is not exist [sourcePath]!!");
          } else if (!fromSmbFile.canRead()) {
            statusParams.putBoolean("success", false);
            statusParams.putString("errorCode", "1111");
            statusParams.putString("message", " no permission  to read [sourcePath]!!");
          } else if (!fromSmbFile.canWrite()) {
            statusParams.putBoolean("success", false);
            statusParams.putString("errorCode", "1111");
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
            if (toSmbFile.exists()) {
              statusParams.putBoolean("success", false);
              statusParams.putString("errorCode", "1111");
              statusParams.putString("message", "file in destination path [" + toSmbFile.getPath() + "] exist!!!!");
            } else {
              if (!toSmbFilePath.exists()) toSmbFilePath.mkdirs();
              fromSmbFile.copyTo(toSmbFile);
              if (toSmbFile != null && toSmbFile.exists()) {
                statusParams.putBoolean("success", true);
                statusParams.putString("errorCode", "0000");
                statusParams.putString("message", "successfully copy[" + toSmbFile.getPath() + "]");
              } else {
                statusParams.putBoolean("success", false);
                statusParams.putString("errorCode", "1111");
                statusParams.putString("message", "file not exist in server after copy[" + toSmbFile.getPath() + "]!!!!");
              }
            }
          }
        } catch (Exception e) {
          // Output the stack trace.
          e.printStackTrace();
          statusParams.putBoolean("success", false);
          statusParams.putString("errorCode", "0101");
          statusParams.putString("message", "copy exception error: " + e.getMessage());
        }
        callback.invoke(statusParams);
      }
    });
  }


  @ReactMethod
  public void SMB1MakeDir(
          final String clientId,
          @Nullable final String newPath,
          final Callback callback
  ) {
    extraThreadPool.execute(new Runnable() {
      @Override
      public void run() {
        WritableMap statusParams = Arguments.createMap();
        statusParams.putString("name", "makeDir");
        statusParams.putString("clientId", clientId);
        statusParams.putString("newPath", newPath + "");
        try {
          String fullPath = "/";
          if (newPath != null && !TextUtils.isEmpty(newPath)) {
            fullPath = "/" + newPath + fullPath;
          }
          SmbFile newSmbFile;
          NtlmPasswordAuthentication authentication = authenticationPoolSMB1.get(clientId);
          String serverURL = serverURLPool.get(clientId);
          if (authentication != null) {
            newSmbFile = new SmbFile(serverURL + fullPath, authentication);
          } else {
            newSmbFile = new SmbFile(serverURL + fullPath);
          }

          if (newSmbFile.isFile()) {
            statusParams.putBoolean("success", false);
            statusParams.putString("errorCode", "1111");
            statusParams.putString("message", " can not create a file [sourcePath]!!");
          } else if (newSmbFile.exists()) {
            statusParams.putBoolean("success", false);
            statusParams.putString("errorCode", "1111");
            statusParams.putString("message", " file already exist [sourcePath]!!");
          } else {
            newSmbFile.mkdirs();
            if (newSmbFile != null && newSmbFile.exists()) {
              statusParams.putBoolean("success", true);
              statusParams.putString("errorCode", "0000");
              statusParams.putString("message", "directory successfully created[" + newSmbFile.getPath() + "]");
            } else {
              statusParams.putBoolean("success", false);
              statusParams.putString("errorCode", "1111");
              statusParams.putString("message", "directory not exist in server after creation[" + newSmbFile.getPath() + "]!!!!");
            }

          }
        } catch (Exception e) {
          // Output the stack trace.
          e.printStackTrace();
          statusParams.putBoolean("success", false);
          statusParams.putString("errorCode", "0101");
          statusParams.putString("message", "make directory exception error: " + e.getMessage());
        }
        callback.invoke(statusParams);
      }
    });
  }

  @ReactMethod
  public void SMB1Delete(
          final String clientId,
          @Nullable final String targetPath,
          final Callback callback
  ) {
    extraThreadPool.execute(new Runnable() {
      @Override
      public void run() {
        WritableMap statusParams = Arguments.createMap();
        statusParams.putString("name", "delete");
        statusParams.putString("clientId", clientId);
        statusParams.putString("targetPath", targetPath + "");
        try {
          String fullTargetPath = "/";
          if (targetPath != null && !TextUtils.isEmpty(targetPath)) {
            fullTargetPath = fullTargetPath + targetPath;
          }
          SmbFile targetSmbFile;
          NtlmPasswordAuthentication authentication = authenticationPoolSMB1.get(clientId);
          String serverURL = serverURLPool.get(clientId);
          if (authentication != null) {
            targetSmbFile = new SmbFile(serverURL + fullTargetPath, authentication);
          } else {
            targetSmbFile = new SmbFile(serverURL + fullTargetPath);
          }

          if (!targetSmbFile.exists()) {
            statusParams.putBoolean("success", false);
            statusParams.putString("errorCode", "1111");
            statusParams.putString("message", " can not find file or directory to delete [" + targetSmbFile.getPath() + "]!!");
          } else if (!targetSmbFile.canWrite()) {
            statusParams.putBoolean("success", false);
            statusParams.putString("errorCode", "1111");
            statusParams.putString("message", " no permission to delete file or directory [" + targetSmbFile.getPath() + "]!!");
          } else {
            targetSmbFile.delete();

            statusParams.putBoolean("success", true);
            statusParams.putString("errorCode", "0000");
            statusParams.putString("message", "directory or file successfully deleted[" + targetSmbFile.getPath() + "]");
          }
        } catch (Exception e) {
          // Output the stack trace.
          e.printStackTrace();
          statusParams.putBoolean("success", false);
          statusParams.putString("errorCode", "0101");
          statusParams.putString("message", "delete exception error: " + e.getMessage());
        }
        callback.invoke(statusParams);
      }
    });
  }

  @ReactMethod
  public void SMB1Disconnect(
          final String clientId,
          final Callback callback
  ) {
    WritableMap statusParams = Arguments.createMap();
    statusParams.putString("name", "disconnect");
    statusParams.putString("clientId", clientId);
    try {

      //serverURLPool
      serverURLPool.remove(clientId);
      //authenticationPoolSMB1
      authenticationPoolSMB1.remove(clientId);

      //uploadPool
      //get user's uploads then cancel all

      List<String> uploadIds = clientUploadsPool.get(clientId);
      if (uploadIds != null && !uploadIds.isEmpty()) {
        for (int i = 0; i < uploadIds.size(); i++) {
          uploadPool.remove(uploadIds.get(i));
        }
      }

      //downloadPool
      //get user's downloads then cancel all

      List<String> downloadIds = clientDownloadsPool.get(clientId);
      if (downloadIds != null && !downloadIds.isEmpty()) {
        for (int i = 0; i < downloadIds.size(); i++) {
          downloadPool.remove(downloadIds.get(i));
        }
      }

      statusParams.putBoolean("success", true);
      statusParams.putString("errorCode", "0000");
      statusParams.putString("message", "client [" + clientId + "] disconnected successfully");

    } catch (Exception e) {
      // Output the stack trace.
      e.printStackTrace();
      statusParams.putBoolean("success", false);
      statusParams.putString("errorCode", "0101");
      statusParams.putString("message", "disconnect exception error[" + clientId + "]: " + e.getMessage());
    }
    callback.invoke(statusParams);
  }


  @ReactMethod
  public void SMB1Test(String workGroup, String ip, String username, String password, String sharedFolder, String fileName) {
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


  /**
   * SMB 2&3 supported apis (used SMBJ)
   *
   */

  @ReactMethod
  public void connect(
          final String clientId,
          final ReadableMap options,
          final Callback callback
  ) {
    extraThreadPool.execute(new Runnable() {
      @Override
      public void run() {
        WritableMap params = Arguments.createMap();
        params.putString("name", "connect");
        params.putString("clientId", clientId);

        String workGroup = "", username = "", password = "";
        String IP = "", port = "", sharedFolder = "";
        AuthenticationContext authentication;

        try {
          workGroup = options.hasKey("workGroup") ? options.getString("workGroup") : "";
          username = options.hasKey("username") ? options.getString("username") : "";
          password = options.hasKey("password") ? options.getString("password") : "";
          IP = options.hasKey("IP") ? options.getString("IP") : "";
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

        if(TextUtils.isEmpty(IP) || TextUtils.isEmpty(sharedFolder)) {
          String emptyFields = "";
          if (TextUtils.isEmpty(IP)) emptyFields += "IP";
          if (TextUtils.isEmpty(sharedFolder)) {
            if (!TextUtils.isEmpty(emptyFields)) emptyFields += ", ";
            emptyFields += "sharedFolder";
          }
          params.putBoolean("success", false);
          params.putString("errorCode", "1002");
          params.putString("message", emptyFields + " could nut be empty and must set!!! ");
          callback.invoke(params);
          return;

        }


        try {
          if (!TextUtils.isEmpty(username) && !TextUtils.isEmpty(password)) {
            authentication = new AuthenticationContext("" + username, password.toCharArray(), "" + workGroup);
          }else{
            authentication = AuthenticationContext.anonymous();
          }
          authenticationPool.put(clientId, authentication);

        } catch (Exception e) {
          // AuthenticationContext creation error
          e.printStackTrace();
          params.putBoolean("success", false);
          params.putString("errorCode", "1002");
          params.putString("message", "exception in creating authentication: " + e.getMessage());
          callback.invoke(params);
          return;
        }

        try {
          SMBClient client = new SMBClient();

          Connection connection;
          if (TextUtils.isEmpty(port)) {
            connection = client.connect("" + IP);
          } else {
            connection = client.connect("" + IP, Integer.parseInt("" + port));

          }
          connectionPool.put(clientId, connection);
          Session session = connection.authenticate(authentication);

          DiskShare share = (DiskShare) session.connectShare("" + sharedFolder);
          diskSharePool.put(clientId, share);

          for (FileIdBothDirectoryInformation f : share.list("", "*")) {
            System.out.println("File : " + f.getFileName());
          }


          params.putBoolean("success", true);
          params.putString("errorCode", "0000");
          params.putString("serverIP", IP);
          params.putString("serverPort", port);
          params.putString("sharedFolder", sharedFolder);
          callback.invoke(params);
          return;

        } catch (Exception e) {
          // server connection error
          e.printStackTrace();
          params.putBoolean("success", false);
          params.putString("errorCode", "1003");
          params.putString("message", "exception in connecting server url: " + e.getMessage());
          callback.invoke(params);
          return;
        }
      }
    });
  }


  public boolean isConnected(
          final String clientId
  ) {

    Connection connection = connectionPool.get(clientId);
    boolean isConnected = false;
    if(connection != null && connection.isConnected()) isConnected = true;

    return isConnected;
  }

  public String concatSMBPathWithName(String path, String name) {
    String reformedPath = "";
    if (path != null && !TextUtils.isEmpty(path))
      reformedPath = path.replace("/", "\\");
    if (!reformedPath.endsWith("\\")) reformedPath = reformedPath + "\\";
    if (TextUtils.equals(reformedPath, "\\")) reformedPath = "";
    String reformedName = "";
    if (name != null && !TextUtils.isEmpty(name))
      reformedName = name.replace("/", "\\");
    if (reformedName.startsWith("\\"))
      reformedName = reformedName.substring("\\".length());
    String combinedPath = "" + reformedPath + reformedName;
    return combinedPath;
  }

  public String concatDevicePathWithName(String path, String name) {
    String reformedPath = "";
    if (path != null && !TextUtils.isEmpty(path)) reformedPath = path;
    if (!reformedPath.endsWith(File.separator)) reformedPath = reformedPath + File.separator;
    String reformedName = "";
    if (name != null && !TextUtils.isEmpty(name)) reformedName = name;
    if (reformedName.startsWith("/"))
      reformedName = reformedName.substring("/".length());
    String combinedPath = "" + reformedPath + reformedName;
    return combinedPath;
  }

  @ReactMethod
  public void connectionStatus(
          final String clientId,
          final Callback callback
  ) {

    WritableMap params = Arguments.createMap();
    params.putString("name", "connectionStatus");
    params.putString("clientId", clientId);

    try {
      Connection connection = connectionPool.get(clientId);
      boolean isConnected = false;
      if (connection != null && connection.isConnected()) isConnected = true;


      if (!isConnected) {
        params.putString("status", "disconnected");
        params.putString("message", "connection disconnected!!! ");
      } else {
        params.putString("status", "connected");
        params.putString("message", "connection is connected!!! ");

      }

      params.putBoolean("success", true);
      params.putString("errorCode", "0000");

    } catch (Exception e) {
      // Output the stack trace.
      e.printStackTrace();
      params.putBoolean("success", false);
      params.putString("errorCode", "0101");
      params.putString("message", "exception error: " + e.getMessage());
    }

    callback.invoke(params);
  }

  @ReactMethod
  public void checkItemType(
          final String clientId,
          final String itemParentPath,
          final String itemName,
          final Callback callback
  ) {

    WritableMap params = Arguments.createMap();
    params.putString("name", "checkItemType");
    params.putString("clientId", clientId);
    params.putString("itemParentPath", itemParentPath);
    params.putString("itemName", itemName);

    try {
      DiskShare share = diskSharePool.get(clientId);
      boolean isFileExist = false;
      String parentPath = "";
      parentPath = itemParentPath.replace("/","\\");
      if(!parentPath.endsWith("\\"))parentPath = parentPath + "\\";
      if(parentPath == "\\") parentPath =  "";
      String itemPath = parentPath + itemName;
      if(!isConnected(clientId)){
        params.putBoolean("success", false);
        params.putString("errorCode", "1010");
        params.putString("message", "connection disconnected!!! ");
        callback.invoke(params);
        return;
      }
      // List<FileIdBothDirectoryInformation> f = share.list("" + itemParentPath, "*");
      // FileIdBothDirectoryInformation itemInfo = f.get(0);

      FileIdBothDirectoryInformation itemInfo = null;

      for (FileIdBothDirectoryInformation f : share.list(""+itemParentPath, "*")) {
        if(f.getFileName().equals(itemName)) itemInfo = f;
//        System.out.println("if condition1: " + f.getFileName().equals(itemName));
//        System.out.println("if condition2: " + f.getFileName() == itemName);
//        System.out.println("itemName : " + itemName);
//        System.out.println("f.getFileName() : " + f.getFileName());
      }

      if(itemInfo == null){
        params.putBoolean("success", false);
        params.putString("errorCode", "1010");
        params.putString("message", "item not exist!!! ");
        callback.invoke(params);
        return;
      }else {


        Boolean isDirectory = EnumWithValue.EnumUtils.isSet(itemInfo.getFileAttributes(), FileAttributes.FILE_ATTRIBUTE_DIRECTORY);
        if (isDirectory) {
          params.putString("itemType", "folder");
        } else {
          params.putString("itemType", "file");
        }
        params.putBoolean("success", true);
        params.putString("errorCode", "0000");
        params.putString("message", "item type extracted!!! ");
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

  @ReactMethod
  public void isFileExist(
          final String clientId,
          final String filePath,
          final Callback callback
  ) {

    WritableMap params = Arguments.createMap();
    params.putString("name", "isFileExist");
    params.putString("clientId", clientId);
    params.putString("filePath", filePath);

    try {
      DiskShare share = diskSharePool.get(clientId);
      boolean isFileExist = false;

      if (share != null && share.fileExists(filePath)) isFileExist = true;

      if (!isFileExist) {
        params.putBoolean("isExist", false);
        params.putString("message", "file not exist!!! ");
      } else {
        params.putBoolean("isExist", true);
        params.putString("message", "file exist!!! ");
      }

      params.putBoolean("success", true);
      params.putString("errorCode", "0000");

    } catch (Exception e) {
      // Output the stack trace.
      e.printStackTrace();
      params.putBoolean("success", false);
      params.putString("errorCode", "0101");
      params.putString("message", "exception error: " + e.getMessage());
    }
    callback.invoke(params);
  }

  @ReactMethod
  public void isFolderExist(
          final String clientId,
          final String folderPath,
          final Callback callback

  ) {

    WritableMap params = Arguments.createMap();
    params.putString("name", "isFolderExist");
    params.putString("clientId", clientId);
    params.putString("folderPath", folderPath);

    try {
      DiskShare share = diskSharePool.get(clientId);
      boolean isFolderExist = false;

      if (share != null && share.folderExists(folderPath)) isFolderExist = true;

      if (!isFolderExist) {
        params.putBoolean("isExist", false);
        params.putString("message", "folder not exist!!! ");
      } else {
        params.putBoolean("isExist", true);
        params.putString("message", "folder exist!!! ");
      }

      params.putBoolean("success", true);
      params.putString("errorCode", "0000");

    } catch (Exception e) {
      // Output the stack trace.
      e.printStackTrace();
      params.putBoolean("success", false);
      params.putString("errorCode", "0101");
      params.putString("message", "exception error: " + e.getMessage());
    }
    callback.invoke(params);
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

        if(!isConnected(clientId)){
          params.putBoolean("success", false);
          params.putString("errorCode", "1010");
          params.putString("message", "connection disconnected!!! ");
          callback.invoke(params);
          return;
        }

        try {
          String destinationPath = "";
          if (path != null && !TextUtils.isEmpty(path)) {
//            destinationPath = "/" + path + destinationPath;
            destinationPath = "" + path;
          }

          DiskShare share = diskSharePool.get(clientId);
          if(share == null){
            params.putBoolean("success", false);
            params.putString("errorCode", "1111");
            params.putString("message", "connection error!!! ");
            callback.invoke(params);
            return;
          }
          if(!share.folderExists(destinationPath)){
            params.putBoolean("success", false);
            params.putString("errorCode", "1111");
            params.putString("message", "path not exist!!! ");
            callback.invoke(params);
            return;
          }

          WritableArray list = Arguments.createArray();
          for (FileIdBothDirectoryInformation f : share.list("" + destinationPath, "*")) {
            //System.out.println("File : " + f.getFileName());

            WritableMap currentFile = Arguments.createMap();
            currentFile.putString("name", f.getFileName());
            currentFile.putString("shortName", f.getShortName());

            //boolean
            currentFile.putBoolean("isDirectory", EnumWithValue.EnumUtils.isSet(f.getFileAttributes(), FileAttributes.FILE_ATTRIBUTE_DIRECTORY));
            currentFile.putBoolean("readOnly", EnumWithValue.EnumUtils.isSet(f.getFileAttributes(), FileAttributes.FILE_ATTRIBUTE_READONLY));
            currentFile.putBoolean("hidden", EnumWithValue.EnumUtils.isSet(f.getFileAttributes(), FileAttributes.FILE_ATTRIBUTE_HIDDEN));

            //long
            currentFile.putString("createTime", String.valueOf(f.getCreationTime()));
            currentFile.putString("lastModified", String.valueOf(f.getLastWriteTime()));
            currentFile.putString("size", String.valueOf(f.getEndOfFile()));

            list.pushMap(currentFile);
          }

          params.putBoolean("success", true);
          params.putString("errorCode", "0000");
          params.putString("message", "path [" + path + "] list successfully.");
          params.putArray("list", list);

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
          final String clientId,
          final String downloadId,
          @Nullable final String fromPath,
          @Nullable final String toPath,
          final String fileName,
          final Callback callback
  ) {

    downloadThreadPool.execute(new Runnable() {
      @Override
      public void run() {
        WritableMap statusParams = Arguments.createMap();
        statusParams.putString("name", "download");
        statusParams.putString("clientId", clientId);
        statusParams.putString("downloadId", downloadId);
        statusParams.putString("fileName", fileName + "");
        statusParams.putString("fromPath", fromPath + "");
        statusParams.putString("toPath", toPath + "");
        if(!isConnected(clientId)){
          statusParams.putBoolean("success", false);
          statusParams.putString("errorCode", "1010");
          statusParams.putString("message", "connection disconnected!!! ");
          callback.invoke(statusParams);
          return;
        }
        DiskShare share = diskSharePool.get(clientId); //smbShare
        FileIdBothDirectoryInformation srcFileInfo = null;
        com.hierynomus.smbj.share.File srcFile = null;
        //SmbFile srcFile = null;
        File destFile = null;
        boolean isDownloadInitialized = false;
        try {
          if (checkWriteExternalStoragePermissions()) {
            String sourceFilePath = concatSMBPathWithName(fromPath,fileName);

            if (!share.fileExists(sourceFilePath)) {
              statusParams.putBoolean("success", false);
              statusParams.putString("errorCode", "1111");
              statusParams.putString("message", "File [" + sourceFilePath + "] is not exist!!");
              callback.invoke(statusParams);
              return;
            }

            srcFile = share.openFile(sourceFilePath,
                    EnumSet.of(AccessMask.GENERIC_READ), null, SMB2ShareAccess.ALL,
                    SMB2CreateDisposition.FILE_OPEN, null);
            srcFileInfo = share.list(fromPath,fileName).get(0);

            //source file initialized successfully, now initializing destination file

            String destinationFilePath = concatDevicePathWithName(toPath, fileName);

            //File destFile;
            destFile = new File(destinationFilePath);

            //destination file initialized successfully

            statusParams.putString("destPath", "" + destFile.getAbsolutePath());
            statusParams.putBoolean("success", true);
            statusParams.putString("errorCode", "0000");
            statusParams.putString("message", "download initialized successfully!!! ");

            isDownloadInitialized = true;
          } else {
            statusParams.putBoolean("success", false);
            statusParams.putString("errorCode", "1111");
            statusParams.putString("message", "no permission to access device storage!!! ");
          }
        } catch (Exception e) {
          // Output the stack trace.
          e.printStackTrace();
          statusParams.putBoolean("success", false);
          statusParams.putString("errorCode", "0101");
          statusParams.putString("message", "download exception error: " + e.getMessage());
        }
        callback.invoke(statusParams);

        if (isDownloadInitialized && srcFile != null && destFile != null) {
          try {
            BufferedInputStream inBuf = new BufferedInputStream(srcFile.getInputStream());
            OutputStream out = new FileOutputStream(destFile);

            // write the bits from Instream to Outstream
            //byte[] buf = new byte[16 * 1024 * 1024];
            byte[] buf = new byte[100 * 1024];
            int len;
            long totalSize = srcFileInfo.getEndOfFile();
            long downloadedSize = 0;

            List<String> downloadIds = clientDownloadsPool.remove(clientId);
            if (downloadIds == null || downloadIds.isEmpty()) downloadIds = new ArrayList<String>();
            if (downloadIds.indexOf(downloadId) == -1) downloadIds.add(downloadId);
            clientDownloadsPool.put(clientId, downloadIds);

            downloadPool.put(downloadId, "inProgress");
            String downloadStatus = "inProgress";

            while ((len = inBuf.read(buf)) > 0) {
              downloadStatus = downloadPool.get(downloadId);
              if (downloadStatus == null) downloadStatus = "cancel";

              out.write(buf, 0, len);
              downloadedSize += len;

              boolean isCompleted = totalSize == downloadedSize;
              String message = "downloading";
              String status = "downloading";

              if (isCompleted) {
                message = "download completed successfully.";
                status = "completed";
              } else if (downloadStatus == "cancel") {
                message = "download canceled";
                status = "canceled";
              }

              WritableMap params = Arguments.createMap();
              params.putString("name", "downloadProgress");
              params.putBoolean("success", true);
              params.putBoolean("completed", isCompleted);
              params.putString("errorCode", "0000");
              params.putString("message", message);
              params.putString("status", status);
              params.putString("clientId", clientId);
              params.putString("downloadId", downloadId);
              params.putString("fileName", fileName + "");
              params.putString("fromPath", fromPath + "");
              params.putString("toPath", toPath + "");
              params.putString("srcPath", "" + share.getSmbPath() + '\\' + fromPath);
              params.putString("destPath", "" + destFile.getAbsolutePath());
              params.putString("totalSize", totalSize + "");
              params.putString("downloadedSize", downloadedSize + "");
              sendEvent(reactContext, "SMBDownloadProgress", params);

              if (downloadStatus == "cancel") {
                break;
              }
            }
            inBuf.close();
            out.close();

            if (downloadStatus == "cancel") {
              destFile.delete();
            }

          } catch (Exception e) {
            // Output the stack trace.
            e.printStackTrace();
            WritableMap params = Arguments.createMap();
            params.putString("name", "downloadProgress");
            params.putString("clientId", clientId);
            params.putString("downloadId", downloadId);
            params.putString("fileName", fileName + "");
            params.putString("fromPath", fromPath + "");
            params.putString("toPath", toPath + "");
            params.putString("srcPath", "" + share.getSmbPath() + '\\' + fromPath);
            params.putString("destPath", "" + destFile.getAbsolutePath());
            params.putBoolean("success", false);
            params.putString("errorCode", "0101");
            params.putString("message", "download progress exception error: " + e.getMessage());
            sendEvent(reactContext, "SMBDownloadProgress", params);
          }

          downloadPool.remove(downloadId);
          List<String> downloadIds = clientDownloadsPool.remove(clientId);
          if (downloadIds != null && !downloadIds.isEmpty()) {
            downloadIds.remove(downloadId);
            clientDownloadsPool.put(clientId, downloadIds);
          }
        }
      }
    });

  }

  @ReactMethod
  public void cancelDownload(
          final String clientId,
          final String downloadId
  ) {
    try {
      if (downloadPool.containsKey(downloadId))
        downloadPool.put(downloadId, "cancel");
    } catch (Exception e) {
      // Output the stack trace.
      e.printStackTrace();
    }
  }

  @ReactMethod
  public void upload(
          final String clientId,
          final String uploadId,
          @Nullable final String fromPath,
          @Nullable final String toPath,
          final String fileName,
          final Callback callback
  ) {
    uploadThreadPool.execute(new Runnable() {
      @Override
      public void run() {

        WritableMap statusParams = Arguments.createMap();
        statusParams.putString("name", "upload");
        statusParams.putString("clientId", clientId);
        statusParams.putString("uploadId", uploadId);
        statusParams.putString("fileName", fileName + "");
        statusParams.putString("fromPath", fromPath + "");
        statusParams.putString("toPath", toPath + "");
        if(!isConnected(clientId)){
          statusParams.putBoolean("success", false);
          statusParams.putString("errorCode", "1010");
          statusParams.putString("message", "connection disconnected!!! ");
          callback.invoke(statusParams);
          return;
        }
        if (fileName == null || TextUtils.isEmpty(fileName)) {
          statusParams.putBoolean("success", false);
          statusParams.putString("errorCode", "1010");
          statusParams.putString("message", "fileName could not be empty!!! ");
          callback.invoke(statusParams);
          return;
        }

        DiskShare share = diskSharePool.get(clientId); //smbShare
        com.hierynomus.smbj.share.File destFile = null;
        String destinationFilePath = "";
        File srcFile = null;
        //SmbFile destFile = null;
        boolean isUploadInitialized = false;

        try {
          if (checkReadExternalStoragePermissions()) {
            String sourceFilePath = concatDevicePathWithName(fromPath,fileName);
            srcFile = new File(sourceFilePath);

            if (srcFile.isDirectory()) {
              statusParams.putBoolean("success", false);
              statusParams.putString("errorCode", "1111");
              statusParams.putString("message", "[" + sourceFilePath + "] is a directory!!");
              callback.invoke(statusParams);
              return;
            }
            if (!srcFile.exists()) {
              statusParams.putBoolean("success", false);
              statusParams.putString("errorCode", "1111");
              statusParams.putString("message", "[" + sourceFilePath + "] is not exist!!");
              callback.invoke(statusParams);
              return;
            }
            if (!srcFile.canRead()) {
              statusParams.putBoolean("success", false);
              statusParams.putString("errorCode", "1111");
              statusParams.putString("message", " no permission to read [" + sourceFilePath + "]!!");
              callback.invoke(statusParams);
              return;
            }
            //source file initialized successfully, now initializing destination file

            String destinationParentPath = concatSMBPathWithName(toPath,"");
            destinationFilePath = concatSMBPathWithName(toPath,fileName);

            if(!share.folderExists(destinationParentPath)) {
              String[] separatedPath = destinationParentPath.split("\\\\");
              String madePath = "";
              for (int i = 0; i < separatedPath.length; i++) {
                if (!share.folderExists(madePath + separatedPath[i]))
                  share.mkdir(madePath + separatedPath[i]);
                madePath += separatedPath[i] + "\\";
              }
            }

            destFile = share.openFile(destinationFilePath,
                    EnumSet.of(AccessMask.GENERIC_WRITE), null, SMB2ShareAccess.ALL,
                    SMB2CreateDisposition.FILE_OPEN_IF, null);

            //destination file initialized successfully

            statusParams.putString("destPath", "" + share.getSmbPath() + '\\' + destinationFilePath);
            statusParams.putBoolean("success", true);
            statusParams.putString("errorCode", "0000");
            statusParams.putString("message", "upload initialized successfully!!! ");

            isUploadInitialized = true;

          } else {
            statusParams.putBoolean("success", false);
            statusParams.putString("errorCode", "1111");
            statusParams.putString("message", "no permission to access device storage!!! ");
          }
        } catch (Exception e) {
          // Output the stack trace.
          e.printStackTrace();
          statusParams.putBoolean("success", false);
          statusParams.putString("errorCode", "0101");
          statusParams.putString("message", "upload exception error: " + e.getMessage());
        }

        callback.invoke(statusParams);

        if (isUploadInitialized && srcFile != null && destFile != null) {
          try {
            BufferedInputStream inBuf = new BufferedInputStream(new FileInputStream(srcFile));
            final BufferedOutputStream smbFileOutputStream = new BufferedOutputStream(destFile.getOutputStream());

            // write the bits from Instream to Outstream
            //byte[] buf = new byte[16 * 1024 * 1024];
            byte[] buf = new byte[100 * 1024];
            int len;
            long totalSize = srcFile.length();
            long uploadedSize = 0;

            List<String> uploadIds = clientUploadsPool.remove(clientId);
            if (uploadIds == null || uploadIds.isEmpty()) uploadIds = new ArrayList<String>();
            if (uploadIds.indexOf(uploadId) == -1) uploadIds.add(uploadId);
            clientUploadsPool.put(clientId, uploadIds);

            uploadPool.put(uploadId, "inProgress");
            String uploadStatus = "inProgress";

            while ((len = inBuf.read(buf)) > 0) {
              uploadStatus = uploadPool.get(uploadId);
              if (uploadStatus == null) uploadStatus = "cancel";

              smbFileOutputStream.write(buf, 0, len);
              uploadedSize += len;

              boolean isCompleted = totalSize == uploadedSize;
              String message = "uploading";
              String status = "uploading";

              if (isCompleted) {
                message = "upload completed successfully.";
                status = "completed";
              } else if (uploadStatus == "cancel") {
                message = "upload canceled";
                status = "canceled";
              }

              WritableMap params = Arguments.createMap();
              params.putString("name", "uploadProgress");
              params.putBoolean("success", true);
              params.putBoolean("completed", isCompleted);
              params.putString("errorCode", "0000");
              params.putString("message", message);
              params.putString("status", status);
              params.putString("clientId", clientId);
              params.putString("uploadId", uploadId);
              params.putString("fileName", srcFile.getName() + "");
              params.putString("fromPath", fromPath + "");
              params.putString("toPath", toPath + "");
              params.putString("srcPath", "" + srcFile.getAbsolutePath());
              params.putString("destPath", "" + share.getSmbPath() + '\\' + destinationFilePath);
              params.putString("totalSize", totalSize + "");
              params.putString("uploadedSize", uploadedSize + "");
              sendEvent(reactContext, "SMBUploadProgress", params);

              if (uploadStatus == "cancel") {
                break;
              }
            }
            inBuf.close();
            smbFileOutputStream.flush();
            smbFileOutputStream.close();

            if (uploadStatus == "cancel") {
              share.rm(destinationFilePath);
              //destFile.delete();
            }

          } catch (Exception e) {
            // Output the stack trace.
            e.printStackTrace();
            WritableMap params = Arguments.createMap();
            params.putString("name", "uploadProgress");
            params.putString("clientId", clientId);
            params.putString("uploadId", uploadId);
            params.putString("fileName", srcFile.getName() + "");
            params.putString("fromPath", fromPath + "");
            params.putString("toPath", toPath + "");
            params.putString("srcPath", "" + srcFile.getAbsolutePath());
            params.putString("destPath", "" + share.getSmbPath() + '\\' + destinationFilePath);
            params.putBoolean("success", false);
            params.putString("errorCode", "0101");
            params.putString("message", "upload progress exception error: " + e.getMessage());
            sendEvent(reactContext, "SMBUploadProgress", params);
          }
          uploadPool.remove(uploadId);

          List<String> uploadIds = clientUploadsPool.remove(clientId);

          if (uploadIds != null && !uploadIds.isEmpty()) {
            uploadIds.remove(uploadId);
            clientUploadsPool.put(clientId, uploadIds);
          }
        }
      }
    });
  }

  @ReactMethod
  public void cancelUpload(
          final String clientId,
          final String uploadId
  ) {
    try {
      if (uploadPool.containsKey(uploadId))
        uploadPool.put(uploadId, "cancel");
    } catch (Exception e) {
      // Output the stack trace.
      e.printStackTrace();
    }
  }

  @ReactMethod
  public void renameFile(
          final String clientId,
          @Nullable final String path,
          final String oldFileName,
          final String newFileName,
          final Boolean replaceIfExist,
          final Callback callback
  ) {
    extraThreadPool.execute(new Runnable() {
      @Override
      public void run() {
        WritableMap statusParams = Arguments.createMap();
        statusParams.putString("name", "renameFile");
        statusParams.putString("clientId", clientId);
        statusParams.putString("path", path + "");
        statusParams.putString("oldFileName", oldFileName + "");
        statusParams.putString("newFileName", newFileName + "");

        DiskShare share = diskSharePool.get(clientId); //smbShare
        if(share == null){
          statusParams.putBoolean("success", false);
          statusParams.putString("errorCode", "1111");
          statusParams.putString("message", "connection error!!! ");
          callback.invoke(statusParams);
          return;
        }

        try {
          String oldFilePATH = concatSMBPathWithName(path, oldFileName);
          String newFilePATH = concatSMBPathWithName(path, newFileName);

          if(!share.fileExists(oldFilePATH)) {
            statusParams.putBoolean("success", false);
            statusParams.putString("errorCode", "1111");
            statusParams.putString("message", "file not exist!!! ");
            callback.invoke(statusParams);
            return;
          }
          if(share.fileExists(newFilePATH) && !replaceIfExist) {
            statusParams.putBoolean("success", false);
            statusParams.putString("errorCode", "1111");
            statusParams.putString("message", "new file name exist!!! ");
            callback.invoke(statusParams);
            return;
          }

          com.hierynomus.smbj.share.File renameFile = share.openFile(
                  oldFilePATH,
                  EnumSet.of(AccessMask.DELETE, AccessMask.GENERIC_WRITE),
                  null,
                  SMB2ShareAccess.ALL,
                  SMB2CreateDisposition.FILE_OPEN,
                  null);

          renameFile.rename(newFilePATH, replaceIfExist);
          renameFile.close();

          statusParams.putBoolean("success", true);
          statusParams.putString("errorCode", "0000");
          statusParams.putString("message", "file successfully renamed[" + oldFileName + " -> " + newFileName + "]");

        } catch (Exception e) {
          // Output the stack trace.
          e.printStackTrace();
          statusParams.putBoolean("success", false);
          statusParams.putString("errorCode", "0101");
          statusParams.putString("message", "file rename exception error: " + e.getMessage());
        }
        callback.invoke(statusParams);
      }
    });
  }

  @ReactMethod
  public void renameFolder(
          final String clientId,
          @Nullable final String path,
          final String oldFolderName,
          final String newFolderName,
          final Boolean replaceIfExist,
          final Callback callback
  ) {
    extraThreadPool.execute(new Runnable() {
      @Override
      public void run() {
        WritableMap statusParams = Arguments.createMap();
        statusParams.putString("name", "renameFolder");
        statusParams.putString("clientId", clientId);
        statusParams.putString("path", path + "");
        statusParams.putString("oldFolderName", oldFolderName + "");
        statusParams.putString("newFolderName", newFolderName + "");

        DiskShare share = diskSharePool.get(clientId); //smbShare
        if(share == null){
          statusParams.putBoolean("success", false);
          statusParams.putString("errorCode", "1111");
          statusParams.putString("message", "connection error!!! ");
          callback.invoke(statusParams);
          return;
        }

        try {
          String oldFolderPATH = concatSMBPathWithName(path, oldFolderName);
          String newFolderPATH = concatSMBPathWithName(path, newFolderName);

          if(!share.folderExists(oldFolderPATH)) {
            statusParams.putBoolean("success", false);
            statusParams.putString("errorCode", "1111");
            statusParams.putString("message", "folder not exist!!! ");
            callback.invoke(statusParams);
            return;
          }
          if(share.folderExists(newFolderPATH) && !replaceIfExist) {
            statusParams.putBoolean("success", false);
            statusParams.putString("errorCode", "1111");
            statusParams.putString("message", "new folder name exist!!! ");
            callback.invoke(statusParams);
            return;
          }
          com.hierynomus.smbj.share.Directory renameFolder = share.openDirectory(
                  oldFolderPATH,
                  EnumSet.of(AccessMask.DELETE, AccessMask.GENERIC_WRITE),
                  null,
                  SMB2ShareAccess.ALL,
                  SMB2CreateDisposition.FILE_OPEN,
                  null);

          renameFolder.rename(newFolderPATH, replaceIfExist);
          renameFolder.close();

          statusParams.putBoolean("success", true);
          statusParams.putString("errorCode", "0000");
          statusParams.putString("message", "folder successfully renamed["+oldFolderName+ " -> " + newFolderName + "]");

        } catch (Exception e) {
          // Output the stack trace.
          e.printStackTrace();
          statusParams.putBoolean("success", false);
          statusParams.putString("errorCode", "0101");
          statusParams.putString("message", "folder rename exception error: " + e.getMessage());
        }
        callback.invoke(statusParams);
      }
    });
  }

  @ReactMethod
  public void fileMoveTo(
          final String clientId,
          @Nullable final String fromPath,
          @Nullable final String toPath,
          final String fileName,
          final Boolean replaceIfExist,
          final Callback callback
  ) {
    extraThreadPool.execute(new Runnable() {
      @Override
      public void run() {
        WritableMap statusParams = Arguments.createMap();
        statusParams.putString("name", "fileMoveTo");
        statusParams.putString("clientId", clientId);
        statusParams.putString("fromPath", fromPath + "");
        statusParams.putString("toPath", toPath + "");
        statusParams.putString("fileName", fileName + "");

        DiskShare share = diskSharePool.get(clientId); //smbShare
        if(share == null){
          statusParams.putBoolean("success", false);
          statusParams.putString("errorCode", "1111");
          statusParams.putString("message", "connection error!!! ");
          callback.invoke(statusParams);
          return;
        }

        try {
          String oldFilePATH = concatSMBPathWithName(fromPath, fileName);
          String newFilePATH = concatSMBPathWithName(toPath, fileName);

          if(!share.fileExists(oldFilePATH)) {
            statusParams.putBoolean("success", false);
            statusParams.putString("errorCode", "1111");
            statusParams.putString("message", "file not exist!!! ");
            callback.invoke(statusParams);
            return;
          }
          if(share.fileExists(newFilePATH) && !replaceIfExist) {
            statusParams.putBoolean("success", false);
            statusParams.putString("errorCode", "1111");
            statusParams.putString("message", "file exist in destination path!!! ");
            callback.invoke(statusParams);
            return;
          }

          com.hierynomus.smbj.share.File renameFile = share.openFile(
                  oldFilePATH,
                  EnumSet.of(AccessMask.DELETE, AccessMask.GENERIC_WRITE),
                  null,
                  SMB2ShareAccess.ALL,
                  SMB2CreateDisposition.FILE_OPEN,
                  null);

          renameFile.rename(newFilePATH, replaceIfExist);
          renameFile.close();

          statusParams.putBoolean("success", true);
          statusParams.putString("errorCode", "0000");
          statusParams.putString("message", "file successfully moved[" + oldFilePATH + " -> " + newFilePATH + "]");

        } catch (Exception e) {
          // Output the stack trace.
          e.printStackTrace();
          statusParams.putBoolean("success", false);
          statusParams.putString("errorCode", "0101");
          statusParams.putString("message", "file move exception error: " + e.getMessage());
        }
        callback.invoke(statusParams);
      }
    });
  }

  @ReactMethod
  public void folderMoveTo(
          final String clientId,
          @Nullable final String fromPath,
          @Nullable final String toPath,
          final String folderName,
          final Boolean replaceIfExist,
          final Callback callback
  ) {
    extraThreadPool.execute(new Runnable() {
      @Override
      public void run() {
        WritableMap statusParams = Arguments.createMap();
        statusParams.putString("name", "folderMoveTo");
        statusParams.putString("clientId", clientId);
        statusParams.putString("fromPath", fromPath + "");
        statusParams.putString("toPath", toPath + "");
        statusParams.putString("folderName", folderName + "");

        DiskShare share = diskSharePool.get(clientId); //smbShare
        if(share == null){
          statusParams.putBoolean("success", false);
          statusParams.putString("errorCode", "1111");
          statusParams.putString("message", "connection error!!! ");
          callback.invoke(statusParams);
          return;
        }

        try {
          String oldFolderPATH = concatSMBPathWithName(fromPath, folderName);
          String newFolderPATH = concatSMBPathWithName(toPath, folderName);;

          if(!share.folderExists(oldFolderPATH)) {
            statusParams.putBoolean("success", false);
            statusParams.putString("errorCode", "1111");
            statusParams.putString("message", "folder not exist!!! ");
            callback.invoke(statusParams);
            return;
          }
          if(share.folderExists(newFolderPATH) && !replaceIfExist) {
            statusParams.putBoolean("success", false);
            statusParams.putString("errorCode", "1111");
            statusParams.putString("message", "folder exist in destination path!!! ");
            callback.invoke(statusParams);
            return;
          }

          com.hierynomus.smbj.share.Directory renameFolder = share.openDirectory(
                  oldFolderPATH,
                  EnumSet.of(AccessMask.DELETE, AccessMask.GENERIC_WRITE),
                  null,
                  SMB2ShareAccess.ALL,
                  SMB2CreateDisposition.FILE_OPEN,
                  null);

          renameFolder.rename(newFolderPATH, replaceIfExist);
          renameFolder.close();

          statusParams.putBoolean("success", true);
          statusParams.putString("errorCode", "0000");
          statusParams.putString("message", "folder successfully moved["+oldFolderPATH+ " -> " + newFolderPATH + "]");

        } catch (Exception e) {
          // Output the stack trace.
          e.printStackTrace();
          statusParams.putBoolean("success", false);
          statusParams.putString("errorCode", "0101");
          statusParams.putString("message", "folder move exception error: " + e.getMessage());
        }
        callback.invoke(statusParams);
      }
    });
  }


  @ReactMethod
  public void fileCopyTo(
          final String clientId,
          @Nullable final String fromPath,
          @Nullable final String toPath,
          final String fileName,
          final Boolean replaceIfExist,
          final Callback callback
  ) {
    extraThreadPool.execute(new Runnable() {
      @Override
      public void run() {
        WritableMap statusParams = Arguments.createMap();
        statusParams.putString("name", "fileCopyTo");
        statusParams.putString("clientId", clientId);
        statusParams.putString("fromPath", fromPath + "");
        statusParams.putString("toPath", toPath + "");
        statusParams.putString("fileName", fileName + "");

        DiskShare share = diskSharePool.get(clientId); //smbShare
        if(share == null){
          statusParams.putBoolean("success", false);
          statusParams.putString("errorCode", "1111");
          statusParams.putString("message", "connection error!!! ");
          callback.invoke(statusParams);
          return;
        }

        try {
          String fromFilePATH = concatSMBPathWithName(fromPath, fileName);;
          String toFilePATH = concatSMBPathWithName(toPath, fileName);;

          if(!share.fileExists(fromFilePATH)) {
            statusParams.putBoolean("success", false);
            statusParams.putString("errorCode", "1111");
            statusParams.putString("message", "file not exist!!! ");
            callback.invoke(statusParams);
            return;
          }
          if(share.fileExists(toFilePATH) && !replaceIfExist) {
            statusParams.putBoolean("success", false);
            statusParams.putString("errorCode", "1111");
            statusParams.putString("message", "file exist in destination path!!! ");
            callback.invoke(statusParams);
            return;
          }

          com.hierynomus.smbj.share.File fileFrom = share.openFile(
                  fromFilePATH,
                  EnumSet.of(AccessMask.GENERIC_READ),
                  null,
                  SMB2ShareAccess.ALL,
                  SMB2CreateDisposition.FILE_OPEN,
                  null);

          SMB2CreateDisposition smb2CreateDisposition =SMB2CreateDisposition.FILE_CREATE;
          if(replaceIfExist) smb2CreateDisposition =SMB2CreateDisposition.FILE_OVERWRITE_IF;

          com.hierynomus.smbj.share.File fileTo = share.openFile(
                  toFilePATH,
                  EnumSet.of(AccessMask.GENERIC_WRITE),
                  null,
                  SMB2ShareAccess.ALL,
                  smb2CreateDisposition,
                  null);

          fileFrom.remoteCopyTo(fileTo);
          fileFrom.close();
          fileTo.close();

          statusParams.putBoolean("success", true);
          statusParams.putString("errorCode", "0000");
          statusParams.putString("message", "file successfully copied[" + fromFilePATH + " -> " + toFilePATH + "]");


        } catch (Exception e) {
          // Output the stack trace.
          e.printStackTrace();
          statusParams.putBoolean("success", false);
          statusParams.putString("errorCode", "0101");
          statusParams.putString("message", "file copy exception error: " + e.getMessage());
        }
        callback.invoke(statusParams);
      }
    });
  }


  @ReactMethod
  public void makeDir(
          final String clientId,
          @Nullable final String path,
          final String folderName,
          final Callback callback
  ) {
    extraThreadPool.execute(new Runnable() {
      @Override
      public void run() {
        WritableMap statusParams = Arguments.createMap();
        statusParams.putString("name", "makeDir");
        statusParams.putString("clientId", clientId);
        statusParams.putString("path", path + "");
        statusParams.putString("folderName", folderName + "");

        DiskShare share = diskSharePool.get(clientId); //smbShare
        if(share == null){
          statusParams.putBoolean("success", false);
          statusParams.putString("errorCode", "1111");
          statusParams.putString("message", "connection error!!! ");
          callback.invoke(statusParams);
          return;
        }

        try {
          String folderPATH = concatSMBPathWithName(path, folderName);;

          if(share.folderExists(folderPATH)) {
            statusParams.putBoolean("success", false);
            statusParams.putString("errorCode", "1111");
            statusParams.putString("message", "folder exist in the path!!! ");
            callback.invoke(statusParams);
            return;
          }

          share.mkdir(folderPATH);

          statusParams.putBoolean("success", true);
          statusParams.putString("errorCode", "0000");
          statusParams.putString("message", "directory successfully created[" + folderPATH + "]");


        } catch (Exception e) {
          // Output the stack trace.
          e.printStackTrace();
          statusParams.putBoolean("success", false);
          statusParams.putString("errorCode", "0101");
          statusParams.putString("message", "make directory exception error: " + e.getMessage());
        }
        callback.invoke(statusParams);
      }
    });
  }

  @ReactMethod
  public void deleteFile(
          final String clientId,
          @Nullable final String path,
          final String fileName,
          final Callback callback
  ) {
    extraThreadPool.execute(new Runnable() {
      @Override
      public void run() {
        WritableMap statusParams = Arguments.createMap();
        statusParams.putString("name", "deleteFile");
        statusParams.putString("clientId", clientId);
        statusParams.putString("path", path + "");
        statusParams.putString("fileName", fileName + "");

        DiskShare share = diskSharePool.get(clientId); //smbShare
        if(share == null) {
          statusParams.putBoolean("success", false);
          statusParams.putString("errorCode", "1111");
          statusParams.putString("message", "connection error!!! ");
          callback.invoke(statusParams);
          return;
        }

        try {
          String filePATH = concatSMBPathWithName(path, fileName);
          if(!share.fileExists(filePATH)) {
            statusParams.putBoolean("success", false);
            statusParams.putString("errorCode", "1111");
            statusParams.putString("message", "file not exist in the path!!! ");
            callback.invoke(statusParams);
            return;
          }

          share.rm(filePATH);

          statusParams.putBoolean("success", true);
          statusParams.putString("errorCode", "0000");
          statusParams.putString("message", "file successfully deleted[" + filePATH + "]");
        } catch (Exception e) {
          // Output the stack trace.
          e.printStackTrace();
          statusParams.putBoolean("success", false);
          statusParams.putString("errorCode", "0101");
          statusParams.putString("message", "delete file exception error: " + e.getMessage());
        }
        callback.invoke(statusParams);
      }
    });
  }

  @ReactMethod
  public void deleteFolder(
          final String clientId,
          @Nullable final String path,
          final String folderName,
          final Boolean recursive,
          final Callback callback
  ) {
    extraThreadPool.execute(new Runnable() {
      @Override
      public void run() {
        WritableMap statusParams = Arguments.createMap();
        statusParams.putString("name", "deleteFolder");
        statusParams.putString("clientId", clientId);
        statusParams.putString("path", path + "");
        statusParams.putString("folderName", folderName + "");

        DiskShare share = diskSharePool.get(clientId); //smbShare
        if(share == null) {
          statusParams.putBoolean("success", false);
          statusParams.putString("errorCode", "1111");
          statusParams.putString("message", "connection error!!! ");
          callback.invoke(statusParams);
          return;
        }

        try {
          String folderPATH = concatSMBPathWithName(path, folderName);;

          if(!share.folderExists(folderPATH)) {
            statusParams.putBoolean("success", false);
            statusParams.putString("errorCode", "1111");
            statusParams.putString("message", "folder not exist in the path!!! ");
            callback.invoke(statusParams);
            return;
          }

          share.rmdir(folderPATH,recursive);

          statusParams.putBoolean("success", true);
          statusParams.putString("errorCode", "0000");
          statusParams.putString("message", "folder successfully deleted[" + folderPATH + "]");
        } catch (Exception e) {
          // Output the stack trace.
          e.printStackTrace();
          statusParams.putBoolean("success", false);
          statusParams.putString("errorCode", "0101");
          statusParams.putString("message", "delete folder directory exception error: " + e.getMessage());
        }
        callback.invoke(statusParams);
      }
    });
  }

  @ReactMethod
  public void disconnect(
          final String clientId,
          final Callback callback
  ) {
    WritableMap statusParams = Arguments.createMap();
    statusParams.putString("name", "disconnect");
    statusParams.putString("clientId", clientId);
    try {

      //serverURLPool
      serverURLPool.remove(clientId);
      //authenticationPool
      authenticationPool.remove(clientId);
      //diskSharePool
      diskSharePool.remove(clientId);
      //connectionPool
      connectionPool.remove(clientId);

      //uploadPool
      //get user's uploads then cancel all

      List<String> uploadIds = clientUploadsPool.get(clientId);
      if (uploadIds != null && !uploadIds.isEmpty()) {
        for (int i = 0; i < uploadIds.size(); i++) {
          uploadPool.remove(uploadIds.get(i));
        }
      }

      clientUploadsPool.remove(clientId);

      //downloadPool
      //get user's downloads then cancel all

      List<String> downloadIds = clientDownloadsPool.get(clientId);
      if (downloadIds != null && !downloadIds.isEmpty()) {
        for (int i = 0; i < downloadIds.size(); i++) {
          downloadPool.remove(downloadIds.get(i));
        }
      }

      clientDownloadsPool.remove(clientId);

      statusParams.putBoolean("success", true);
      statusParams.putString("errorCode", "0000");
      statusParams.putString("message", "client [" + clientId + "] disconnected successfully");

    } catch (Exception e) {
      // Output the stack trace.
      e.printStackTrace();
      statusParams.putBoolean("success", false);
      statusParams.putString("errorCode", "0101");
      statusParams.putString("message", "disconnect exception error[" + clientId + "]: " + e.getMessage());
    }
    callback.invoke(statusParams);
  }


  @ReactMethod
  public void test(String workGroup, String ip, String port, String username, String password, String sharedFolder, String fileName) {
    try {



      if (!TextUtils.isEmpty(username) && !TextUtils.isEmpty(password) && !TextUtils.isEmpty(ip)) {
        SMBClient client = new SMBClient();

        Connection connection;
        if (TextUtils.isEmpty(port)) {
          connection = client.connect("" + ip);
        } else {
          connection = client.connect("" + ip, Integer.parseInt("" + port));

        }
        AuthenticationContext ac = new AuthenticationContext("" + username, password.toCharArray(), "" + workGroup);
        Session session = connection.authenticate(ac);


        if (!TextUtils.isEmpty(sharedFolder)) {
          DiskShare share = (DiskShare) session.connectShare("" + sharedFolder);
          FileAllInformation share2 = share.getFileInformation("/multimedia");
          com.hierynomus.smbj.share.File file;
          //file.
          //              for (FileIdBothDirectoryInformation f : share.list("FOLDER", "*.TXT")) {
          for (FileIdBothDirectoryInformation f : share.list("", "*")) {
            System.out.println("File : " + f.getFileName());
          }
        }
      }
    } catch (Exception e) {
      // Output the stack trace.
      e.printStackTrace();
      String message = " error in testing SMB";
    }
  }

}
