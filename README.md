
# react-native-smb

# _**this is under deployment and not completed yet**_

## Getting started

`$ npm install react-native-smb --save`

### Mostly automatic installation

`$ react-native link react-native-smb`

### Manual installation


#### iOS

1. In XCode, in the project navigator, right click `Libraries` ➜ `Add Files to [your project's name]`
2. Go to `node_modules` ➜ `react-native-smb` and add `RNReactNativeSmb.xcodeproj`
3. In XCode, in the project navigator, select your project. Add `libRNReactNativeSmb.a` to your project's `Build Phases` ➜ `Link Binary With Libraries`
4. Run your project (`Cmd+R`)<

#### Android

1. Open up `android/app/src/main/java/[...]/MainActivity.java`
  - Add `import com.reactlibrary.RNReactNativeSmbPackage;` to the imports at the top of the file
  - Add `new RNReactNativeSmbPackage()` to the list returned by the `getPackages()` method
2. Append the following lines to `android/settings.gradle`:
  	```
  	include ':react-native-smb'
  	project(':react-native-smb').projectDir = new File(rootProject.projectDir, 	'../node_modules/react-native-smb/android')
  	```
3. Insert the following lines inside the dependencies block in `android/app/build.gradle`:
  	```
      compile project(':react-native-smb')
  	```

#### Windows
[Read it! :D](https://github.com/ReactWindows/react-native)

1. In Visual Studio add the `RNReactNativeSmb.sln` in `node_modules/react-native-smb/windows/RNReactNativeSmb.sln` folder to their solution, reference from their app.
2. Open up your `MainPage.cs` app
  - Add `using React.Native.Smb.RNReactNativeSmb;` to the usings at the top of the file
  - Add `new RNReactNativeSmbPackage()` to the `List<IReactPackage>` returned by the `Packages` method


## Usage

import react-native-smb where want to use, 
```javascript
import RNSmb from 'react-native-smb';
```

then init smb server connection properties
```javascript
let options = {
  workGroup: 'WORKGROUP',
  ip: '192.168.1.108',//smb server ip
  username: 'aba',
  password: '121',
  sharedFolder: 'ali',
};

RNSmb.init(options,
  (url) => {
    //success callback 
    console.log('success. url: ' + url);
  }
  ,
  (errorMessage) => {
    //error callback
    console.log('errorMessage: ' + errorMessage);
  },
);
```


test server connectivity
```javascript

const eventEmitter = new NativeEventEmitter(NativeModules.ToastExample);
eventEmitter.addListener('SMBTestConnection', (event) => {
  if (event.success) {
    console.log('TestConnection success message: ' + event.message);
  } else {
    console.log('TestConnection error message: ' + event.message);
  }
});

RNSmb.testConnection();

```

list files and folders in given path of smb server
```javascript
const eventEmitter = new NativeEventEmitter(NativeModules.ToastExample);
   
eventEmitter.addListener('SMBList', (event) => {
  if (event.success) {
    console.log('TestConnection success message: ' + event.message);
    console.log('event: ' + JSON.stringify(event));
  } else {
    console.log('TestConnection error message: ' + event.message);
  }
});

RNSmb.list("target path")
```

to download a file from smb server and save it in Download folder of Android device
```javascript
const eventEmitter = new NativeEventEmitter(NativeModules.ToastExample);
eventEmitter.addListener('SMBDownloadResult', (event) => {
  console.log(JSON.stringify(event));
  if (event.success) {
    console.log('SMBDownloadResult success');
  } else {
    console.log('SMBDownloadResult error');
  }
});

eventEmitter.addListener('SMBDownloadProgress', (data) => {
  console.log('SMBDownloadProgress data:' + JSON.stringify(data));
});
RNSmb.download(
    'file path in smb server',
    'file name in smb server',
);

```

to upload a file from android device local path to a path in SMB server
```javascript
const eventEmitter = new NativeEventEmitter(NativeModules.ToastExample);
eventEmitter.addListener('SMBUploadResult', (event) => {
  console.log(JSON.stringify(event));
  if (event.success) {
    console.log('SMBUploadResult success');
  } else {
    console.log('SMBUploadResult error');
  }
});

eventEmitter.addListener('SMBUploadProgress', (data) => {
  console.log('SMBUploadProgress data:' + JSON.stringify(data));
});
RNSmb.upload(
    'destination file path in smb server',
    'source file path in Android device local path',
    'file name'
);

```

to rename a file at a path in SMB server
```javascript
const eventEmitter = new NativeEventEmitter(NativeModules.ToastExample);
eventEmitter.addListener('SMBRenameResult', (event) => {
  console.log(JSON.stringify(event));
  if (event.success) {
    console.log('SMBRenameResult success');
  } else {
    console.log('SMBRenameResult error');
  }
});

RNSmb.rename(
    'file path in smb server',
    'file old name',
    'file new name'
);

```


```javascript
import RNSmb from 'react-native-smb';

// TODO: What to do with the module?
RNSmb;
```

