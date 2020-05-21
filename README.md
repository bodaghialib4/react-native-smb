
# react-native-smb

### this is a smb client that work only in android (iOS not implemented)
 tested in RN 61.2

## Getting started

`$ npm install react-native-smb --save`

### Mostly automatic installation (for RN < 60)

`$ react-native link react-native-smb`

### Manual installation


#### iOS

iOS not supported

#### Android (for RN < 60)

1. Open up `android/app/src/main/java/[...]/MainActivity.java`
  - Add `import com.reactlibrary.RNReactNativeSmbPackage;` to the imports at the top of the file
  - Add `new RNReactNativeSmbPackage()` to the list returned by the `getPackages()` method
2. Append the following lines to `android/settings.gradle`:
    ```
    include ':react-native-smb'
    project(':react-native-smb').projectDir = new File(rootProject.projectDir,  '../node_modules/react-native-smb/android')
    ```
3. Insert the following lines inside the dependencies block in `android/app/build.gradle`:
    ```
      compile project(':react-native-smb')
    ```


## Usage

### For SMB version 1:
<details>
  <summary>SMB1 details</summary>
  
  
import react-native-smb where want to use, 
```javascript
import {SMB1Client} from 'react-native-smb';
```

then create new SMBClient (and set connection properties)
```javascript
this.smbClient = new SMBClient(
    '0.0.0.0',//ip
    '',//port
    'sharedFolder',//sharedFolder,
    'workGroup',//workGroup,
    'username',//username,
    'password',//password,
    (data) => {//callback - can be null (not setting)
        console.log('new SMBClient data (callback): ' + JSON.stringify(data));
    },
);

this.smbClient.on(
    'init',
    (data) => {
        console.log('new SMBClient data (on init): ' + JSON.stringify(data));
    },
);
```

to catch all errors, set smbClient.on with "error" event name
```javascript
this.smbClient.on(
    'error',
    (data) => {
        console.log('error in SMBClient (on error): ' + JSON.stringify(data));
    },
);
```


test server connectivity of created smbClient
```javascript
this.smbClient.on(
    'testConnection',
    (data) => {
        console.log('testConnection data (on testConnection): ' + JSON.stringify(data));
    },
);
this.smbClient.testConnection(
    (data) => {//callback
        console.log('testConnection data (callback): ' + JSON.stringify(data));
    },
);
```

list files and folders in given path of smb server for created smbClient
```javascript
this.smbClient.on(
    'list',
    (data) => {
        console.log('list data (on list): ' + JSON.stringify(data));
    },
);

this.smbClient.list(
    'target/path/to/list',//the path to list files and folders
    (data) => {//callback
        console.log('list data (callback): ' + JSON.stringify(data));
    },
);
```

to download a file from smb server for created smbClient
```javascript
this.smbClient.on(
    'downloadProgress',
    (data) => {
        console.log('download progress data (on downloadProgress): ' + JSON.stringify(data));
        this.smbClient.cancelDownload(data.downloadId);
    },
);

this.smbClient.on(
    'download',
    (data) => {
        console.log('download data (on download): ' + JSON.stringify(data));
    },
);

this.smbClient.download(
    'from/path',//source path of file to download (in SMB server)
    'to/path',//destination path to save downloaded file (in Android device)
    'file.name',//the name of file to download
    (data) => {//callback
        console.log('download data (callback): ' + JSON.stringify(data));
    },
);
```

to upload a file from android device local path to a path in SMB server
```javascript
this.smbClient.on(
    'uploadProgress',
    (data) => {
        console.log('upload progress data (on uploadProgress): ' + JSON.stringify(data));
        this.smbClient.cancelUpload(data.uploadId)

    },
);

this.smbClient.on(
    'upload',
    (data) => {
        console.log('upload data (on upload): ' + JSON.stringify(data));
    },
);

this.smbClient.upload(
    'from/path',//source path of file to upload (in Android devic)
    'to/path',//destination path to to upload (in SMB server)
    'file.name',//the name of file to upload
    (data) => {//callback
        console.log('upload data (callback): ' + JSON.stringify(data));
    },
);
```

to rename a file at a path in SMB server
```javascript
this.smbClient.on(
    'rename',
    (data) => {
        console.log('rename data (on rename): ' + JSON.stringify(data));
    },
);

this.smbClient.rename(
    'path/of/file', //a path of file to rename in SMB server
    'old.name', //old file name
    'new.name', //new file name
    (data) => {//callback
        console.log('rename data (callback): ' + JSON.stringify(data));
    },
);
```

to move a file at the SMB server side
```javascript
this.smbClient.on(
    'moveTo',
    (data) => {
        console.log('moveTo data (on moveTo): ' + JSON.stringify(data));
    },
);

this.smbClient.moveTo(
    'from/path', //source path of file to move (in SMB server)
    'to/path', //destination path to to move (in SMB server)
    'file.name', //the name of file to move
    (data) => {//callback
        console.log('moveTo data (callback): ' + JSON.stringify(data));
    },
);
```

to copy a file at the SMB server side
```javascript
this.smbClient.on(
    'copyTo',
    (data) => {
        console.log('copyTo data (on copyTo): ' + JSON.stringify(data));
    },
);

this.smbClient.copyTo(
    'from/path', //source path of file to move (in SMB server)
    'to/path', //destination path to to move (in SMB server)
    'file.name', //the name of file to move
    (data) => {//callback
        console.log('copyTo data (callback): ' + JSON.stringify(data));
    },
);
```

to make a directory at the SMB server side
```javascript
this.smbClient.on(
    'makeDir',
    (data) => {
        console.log('makeDir data (on makeDir): ' + JSON.stringify(data));
    },
);

this.smbClient.makeDir(
    'path/to/make', //path of new directory in smb server
    (data) => {//callback
        console.log('makeDir data (callback): ' + JSON.stringify(data));
    },
);
```

to delete a file or directory at the SMB server side
```javascript
this.smbClient.on(
    'delete',
    (data) => {
        console.log('delete data (on delete): ' + JSON.stringify(data));
    },
);

this.smbClient.delete(
    'path/to/delete', //path of a file or directory in smb server to delete
    (data) => {//callback
        console.log('delete data (callback): ' + JSON.stringify(data));
    },
);
```

to disconnect a client from server
```javascript
this.smbClient.on(
    'disconnect',
    (data) => {
        console.log('disconnect data (on disconnect): ' + JSON.stringify(data));
        this.smbClient = null
    },
);

this.smbClient.disconnect(
    (data) => {//callback
        console.log('disconnect data (callback): ' + JSON.stringify(data));
    },
);
````
</details>









### For SMB version 2&3:
<details>
  <summary>SMB 2&3 details</summary>
  
  
import react-native-smb where want to use,
```javascript
import SMBClient from 'react-native-smb';
```

then create new SMBClient (and set connection properties)
```javascript
this.smbClient = new SMBClient(
    '0.0.0.0',//ip
    '',//port
    'sharedFolder',//sharedFolder,
    'workGroup',//workGroup,
    'username',//username,
    'password',//password,
    (data) => {//callback - can be null (not setting)
        console.log('new SMBClient data (callback): ' + JSON.stringify(data));
    },
);

this.smbClient.on(
    'connect',
    (data) => {
        console.log('new SMBClient data (on connect): ' + JSON.stringify(data));
    },
);
```

to catch all errors, set smbClient.on with "error" event name
```javascript
this.smbClient.on(
    'error',
    (data) => {
        console.log('error in SMBClient (on error): ' + JSON.stringify(data));
    },
);
```


check is connected to server
```javascript
let isConnected = this.smbClient.isConnected();
if(isConnected){
    console.log('SMBClient is connected. ' );
}else{
    console.log('SMBClient is disconnected. ' );
}
```


check file exist on server
```javascript
let fileExist = this.smbClient.isFileExist();
if(fileExist){
    console.log('file exist in server. ' );
}else{
    console.log('file not exist in server. ' );
}
```


check folder exist on server
```javascript
let folderExist = this.smbClient.isFolderExist();
if(folderExist){
    console.log('folder exist in server. ' );
}else{
    console.log('folder not exist in server. ' );
}
```


list files and folders in given path of smb server for created smbClient
```javascript
this.smbClient.on(
    'list',
    (data) => {
        console.log('list data (on list): ' + JSON.stringify(data));
    },
);

this.smbClient.list(
    'target/path/to/list',//the path to list files and folders
    (data) => {//callback
        console.log('list data (callback): ' + JSON.stringify(data));
    },
);
```

to download a file from smb server for created smbClient & cancel it
```javascript
this.smbClient.on(
    'downloadProgress',
    (data) => {
        console.log('download progress data (on downloadProgress): ' + JSON.stringify(data));
        this.smbClient.cancelDownload(data.downloadId);
    },
);

this.smbClient.on(
    'download',
    (data) => {
        console.log('download data (on download): ' + JSON.stringify(data));
    },
);

this.smbClient.download(
    'from/path',//source path of file to download (in SMB server)
    'to/path',//destination path to save downloaded file (in Android device)
    'file.name',//the name of file to download
    (data) => {//callback
        console.log('download data (callback): ' + JSON.stringify(data));
    },
);
```

to upload a file from android device local path to a path in SMB server
```javascript
this.smbClient.on(
    'uploadProgress',
    (data) => {
        console.log('upload progress data (on uploadProgress): ' + JSON.stringify(data));
        this.smbClient.cancelUpload(data.uploadId)
    },
);

this.smbClient.on(
    'upload',
    (data) => {
        console.log('upload data (on upload): ' + JSON.stringify(data));
    },
);

this.smbClient.upload(
    'from/path',//source path of file to upload (in Android devic)
    'to/path',//destination path to to upload (in SMB server)
    'file.name',//the name of file to upload
    (data) => {//callback
        console.log('upload data (callback): ' + JSON.stringify(data));
    },
);
```

to rename a file at a path in SMB server
```javascript
this.smbClient.on(
    'renameFile',
    (data) => {
        console.log('rename file data (on renameFile): ' + JSON.stringify(data));
    },
);

this.smbClient.renameFile(
    'path/of/file', //a path of file to rename in SMB server
    'old.name', //old file name
    'new.name', //new file name
    false, //replace if exist
    (data) => {//callback
        console.log('rename file data (callback): ' + JSON.stringify(data));
    },
);
```


to rename a folder at a path in SMB server
```javascript
this.smbClient.on(
    'renameFolder',
    (data) => {
        console.log('rename folder data (on renameFolder): ' + JSON.stringify(data));
    },
);

this.smbClient.renameFolder(
    'path/of/file', //a path of file to rename in SMB server
    'old.name', //old file name
    'new.name', //new file name
    false, //replace if exist
    (data) => {//callback
        console.log('rename folder data (callback): ' + JSON.stringify(data));
    },
);
```


to move a file at the SMB server side
```javascript 
this.smbClient.on( 
    'fileMoveTo', 
    (data) => { 
        console.log('fileMoveTo data (on fileMoveTo): ' + JSON.stringify(data));
    },
);

this.smbClient.fileMoveTo(
    'from/path', //source path of file to move (in SMB server)
    'to/path', //destination path to to move (in SMB server)
    'file.name', //the name of file to move
    false, //replace if exist
    (data) => {//callback
        console.log('fileMoveTo data (callback): ' + JSON.stringify(data));
    }, 
); 
``` 

to move a folder at the SMB server side
```javascript 
this.smbClient.on( 
    'folderMoveTo', 
    (data) => { 
        console.log('folderMoveTo data (on folderMoveTo): ' + JSON.stringify(data));
    },
);

this.smbClient.folderMoveTo(
    'from/path', //source path of file to move (in SMB server)
    'to/path', //destination path to to move (in SMB server)
    'file.name', //the name of file to move
    false, //replace if exist
    (data) => {//callback
        console.log('folderMoveTo data (callback): ' + JSON.stringify(data));
    }, 
); 
``` 

to copy a file at the SMB server side
```javascript
this.smbClient.on(
    'fileCopyTo',
    (data) => {
        console.log('fileCopyTo data (on fileCopyTo): ' + JSON.stringify(data));
    }, 
); 

this.smbClient.fileCopyTo(
    'from/path', //source path of file to move (in SMB server) 
    'to/path', //destination path to to move (in SMB server) 
    'file.name', //the name of file to move 
    false, //replace if exist 
    (data) => {//callback 
        console.log('fileCopyTo data (callback): ' + JSON.stringify(data));
    },
);
```

to make a directory at the SMB server side
```javascript
this.smbClient.on(
    'makeDir',
    (data) => {
        console.log('makeDir data (on makeDir): ' + JSON.stringify(data));
    },
);

this.smbClient.makeDir(
    'path/to/make', //path of new directory in smb server
    'folderName', //the name of folder to create 
    (data) => {//callback
        console.log('makeDir data (callback): ' + JSON.stringify(data));
    },
);
```

</details>

```javascript
import SMBClient from 'react-native-smb';

// TODO: What to do with the module?
RNSmb;
```

