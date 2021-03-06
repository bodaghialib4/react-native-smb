import {NativeModules, DeviceEventEmitter, NativeEventEmitter} from 'react-native';

const {RNSmb} = NativeModules;


const RNSSHClientEmitter = new NativeEventEmitter(RNSmb);

class SMBClient {
    constructor(ip, port, sharedFolder, workGroup, username, password, callback) {
        this.clientId = Math.floor((1 + Math.random()) * 0x10000).toString(16).substring(1);
        this.handlers = {};
        this.ip = ip;
        this.port = port;
        this.sharedFolder = sharedFolder;
        this.workGroup = workGroup;
        this.username = username;
        this.password = password;
        this.currentPath = "";
        this.parentPath = "";

        this.connect(callback);
    }


    _handleEvent(event) {
        if (event && this.clientId === event.clientId) {
            if (this.handlers.hasOwnProperty(event.name)) {
                this.handlers[event.name](event);
            }
            if (!event.success) {
                if (this.handlers.hasOwnProperty("error")) {
                    this.handlers["error"](event);
                }
            }
        }
    }

    on(event, handler) {
        this.handlers[event] = handler;
    }


    //////

    //smbCurrentPath
    currentPath() {
        return this.currentPath
    }

    //smbCurrentPath
    parentPath() {
        return this.parentPath()
    }

    //connect server
    connect(callback) {
        //init RNSmb
        let options = {
            workGroup: this.workGroup,
            IP: this.ip,
            port: this.port,
            username: this.username,
            password: this.password,
            sharedFolder: this.sharedFolder,
        };
        RNSmb.connect(this.clientId, options,
            (data) => {
                //console.log('RNSmb connect success. data: ' + JSON.stringify(data));
                if (callback && typeof callback === "function") {
                    callback(data);
                }
                this._handleEvent(data);
            }
        );
    }

    //smbIsConnection
    connectionStatus(callback) {
        //test connection
        RNSmb.connectionStatus(this.clientId, (data) => {
            //console.log('connectionStatus data: ' + JSON.stringify(data));
            if (callback && typeof callback === "function") {
                callback(data);
            }
            this._handleEvent(data);
        });
    }

    //check item type (file or folder)
    checkItemType(itemParentPath, itemName, callback) {
        console.log("checkItemType called with itemParentPath: " + itemParentPath +"   and itemName: " + itemName)
        RNSmb.checkItemType(this.clientId, itemParentPath, itemName, (data) => {
            //console.log('isFileExist data: ' + JSON.stringify(data));
            if (callback && typeof callback === "function") {
                callback(data);
            }
            this._handleEvent(data);
        });
    }

    //smbIsFileExist
    isFileExist(filePath, callback) {
        RNSmb.isFileExist(this.clientId, filePath, (data) => {
            //console.log('isFileExist data: ' + JSON.stringify(data));
            if (callback && typeof callback === "function") {
                callback(data);
            }
            this._handleEvent(data);
        });
    }

    //smbIsFolderExist
    isFolderExist(folderPath, callback) {
        RNSmb.isFolderExist(this.clientId, folderPath, (data) => {
            //console.log('isFolderExist data: ' + JSON.stringify(data));
            if (callback && typeof callback === "function") {
                callback(data);
            }
            this._handleEvent(data);
        });
    }

    //smbList
    list(path, callback) {
        //list files & folders
        RNSmb.list(this.clientId, path, (data) => {
            //console.log('list data: ' + JSON.stringify(data));
            if (callback && typeof callback === "function") {
                callback(data);
            }
            this._handleEvent(data);
        });
    }

    //smbDownload
    download(fromPath, toPath, fileName, callback) {
        if (!this.downloadProgressListener) {
            this.downloadProgressListener = DeviceEventEmitter.addListener('SMBDownloadProgress', this._handleEvent.bind(this));
        }

        let downloadId = Math.floor((1 + Math.random()) * 0x10000).toString(16).substring(1);
        //test download
        RNSmb.download(
            this.clientId,
            downloadId,
            fromPath, //a path in smb server side
            toPath,//a path in device storage
            fileName,//file name
            (data) => {
                //console.log('download data: ' + JSON.stringify(data));
                if (callback && typeof callback === "function") {
                    callback(data);
                }
                this._handleEvent(data);
            }
        );
    }

    //smbCancelDownload
    cancelDownload(downloadId) {
        RNSmb.cancelDownload(this.clientId, downloadId);
    }

    //smbUpload
    upload(fromPath, toPath, fileName, callback) {
        if (!this.uploadProgressListener) {
            this.uploadProgressListener = DeviceEventEmitter.addListener('SMBUploadProgress', this._handleEvent.bind(this));
        }
        let uploadId = Math.floor((1 + Math.random()) * 0x10000).toString(16).substring(1);

        RNSmb.upload(
            this.clientId,
            uploadId,
            fromPath, //a path in device storage
            toPath,//a path in smb server side
            fileName,//file name
            (data) => {
                //console.log('upload data: ' + JSON.stringify(data));
                if (callback && typeof callback === "function") {
                    callback(data);
                }
                this._handleEvent(data);
            }
        );
    }

    //smbCancelUpload
    cancelUpload(uploadId) {
        RNSmb.cancelUpload(this.clientId, uploadId);
    }

    //smbRenameFile
    renameFile(path, oldName, newName, replaceIfExist, callback) {
        RNSmb.renameFile(
            this.clientId,
            path,//file path in smb server
            oldName,//old file name
            newName,//new file name
            replaceIfExist,
            (data) => {
                //console.log('rename data: ' + JSON.stringify(data));
                if (callback && typeof callback === "function") {
                    callback(data);
                }
                this._handleEvent(data);
            }
        );
    }

    //smbRenameFolder
    renameFolder(path, oldName, newName, replaceIfExist, callback) {
        RNSmb.renameFolder(
            this.clientId,
            path,//file path in smb server
            oldName,//old file name
            newName,//new file name
            replaceIfExist,
            (data) => {
                //console.log('rename data: ' + JSON.stringify(data));
                if (callback && typeof callback === "function") {
                    callback(data);
                }
                this._handleEvent(data);
            }
        );
    }

    //smbFileMoveTo
    fileMoveTo(fromPath, toPath, fileName, replaceIfExist, callback) {
        RNSmb.fileMoveTo(
            this.clientId,
            fromPath,//file path in smb server
            toPath,//old file name
            fileName,//new file name
            replaceIfExist,//replace if exist
            (data) => {
                //console.log('moveTo data: ' + JSON.stringify(data));
                if (callback && typeof callback === "function") {
                    callback(data);
                }
                this._handleEvent(data);
            }
        );
    }

    //smbFolderMoveTo
    folderMoveTo(fromPath, toPath, fileName, replaceIfExist, callback) {
        RNSmb.folderMoveTo(
            this.clientId,
            fromPath,//file path in smb server
            toPath,//old file name
            fileName,//new file name
            replaceIfExist,//replace if exist
            (data) => {
                //console.log('moveTo data: ' + JSON.stringify(data));
                if (callback && typeof callback === "function") {
                    callback(data);
                }
                this._handleEvent(data);
            }
        );
    }

    //smbFileCopyTo
    fileCopyTo(fromPath, toPath, fileName, replaceIfExist, callback) {
        RNSmb.fileCopyTo(
            this.clientId,
            fromPath,//file path in smb server
            toPath,//old file name in smb server
            fileName,//new file name
            replaceIfExist,//replace if exist
            (data) => {
                //console.log('copyTo data: ' + JSON.stringify(data));
                if (callback && typeof callback === "function") {
                    callback(data);
                }
                this._handleEvent(data);
            }
        );
    }

    //smbMkdir
    makeDir(path,newFolderName, callback) {
        RNSmb.makeDir(
            this.clientId,
            path,// path of new directory in smb server
            newFolderName,
            (data) => {
                //console.log('makeDir data: ' + JSON.stringify(data));
                if (callback && typeof callback === "function") {
                    callback(data);
                }
                this._handleEvent(data);
            }
        );
    }

    //smbDeleteFile
    deleteFile(path, fileName, callback) {
        RNSmb.deleteFile(
            this.clientId,
            path,// path of a file in smb server that must delete
            fileName, //name of file
            (data) => {
                //console.log('delete data: ' + JSON.stringify(data));
                if (callback && typeof callback === "function") {
                    callback(data);
                }
                this._handleEvent(data);
            }
        );
    }

    //smbDeleteFolder
    deleteFolder(path,FolderName,recursive, callback) {
        RNSmb.deleteFolder(
            this.clientId,
            path,// path of folder in smb server that must delete
            FolderName,//name of folder
            recursive,
            (data) => {
                //console.log('delete data: ' + JSON.stringify(data));
                if (callback && typeof callback === "function") {
                    callback(data);
                }
                this._handleEvent(data);
            }
        );
    }

    //smbDisconnect
    disconnect(callback) {
        if (this.downloadProgressListener) {
            this.downloadProgressListener.remove();
            this.downloadProgressListener = null;
        }
        if (this.uploadProgressListener) {
            this.uploadProgressListener.remove();
            this.uploadProgressListener = null;
        }
        RNSmb.disconnect(
            this.clientId,
            (data) => {
                //console.log('disconnect data: ' + JSON.stringify(data));
                if (callback && typeof callback === "function") {
                    callback(data);
                }
                this._handleEvent(data);
            }
        );
    }
}


class SMB1Client {
    constructor(ip, port, sharedFolder, workGroup, username, password, callback) {
        this.clientId = Math.floor((1 + Math.random()) * 0x10000).toString(16).substring(1);
        this.handlers = {};
        this.ip = ip;
        this.port = port;
        this.sharedFolder = sharedFolder;
        this.workGroup = workGroup;
        this.username = username;
        this.password = password;
        this.currentPath = "";
        this.parentPath = "";

        this.initOptions(callback);
    }


    _handleEvent(event) {
        if (event && this.clientId === event.clientId) {
            if (this.handlers.hasOwnProperty(event.name)) {
                this.handlers[event.name](event);
            }
            if (!event.success) {
                if (this.handlers.hasOwnProperty("error")) {
                    this.handlers["error"](event);
                }
            }
        }
    }

    on(event, handler) {
        this.handlers[event] = handler;
    }


    //////

    //smbCurrentPath
    currentPath() {
        return this.currentPath
    }

    //smbCurrentPath
    parentPath() {
        return this.parentPath()
    }

    //smbInitOptions
    initOptions(callback) {
        //init RNSmb
        let options = {
            workGroup: this.workGroup,
            ip: this.ip,
            port: this.port,
            username: this.username,
            password: this.password,
            sharedFolder: this.sharedFolder,
        };
        RNSmb.SMB1Init(this.clientId, options,
            (data) => {
                //console.log('RNSmb init success. data: ' + JSON.stringify(data));
                if (callback && typeof callback === "function") {
                    callback(data);
                }
                this._handleEvent(data);
            }
        );
    }

    //smbTestConnection
    testConnection(callback) {
        //test connection
        RNSmb.SMB1TestConnection(this.clientId,
            (data) => {
                //console.log('testConnection data: ' + JSON.stringify(data));
                if (callback && typeof callback === "function") {
                    callback(data);
                }
                this._handleEvent(data);
            }
        );
    }

    //smbList
    list(path, callback) {
        //list files & folders
        RNSmb.SMB1List(this.clientId, path, (data) => {
            //console.log('list data: ' + JSON.stringify(data));
            if (callback && typeof callback === "function") {
                callback(data);
            }
            this._handleEvent(data);
        });
    }

    //smbDownload
    download(fromPath, toPath, fileName, callback) {
        if (!this.downloadProgressListener) {
            this.downloadProgressListener = DeviceEventEmitter.addListener('SMBDownloadProgress', this._handleEvent.bind(this));
        }

        let downloadId = Math.floor((1 + Math.random()) * 0x10000).toString(16).substring(1);
        //test download
        RNSmb.SMB1Download(
            this.clientId,
            downloadId,
            fromPath, //a path in smb server side
            toPath,//a path in device storage
            fileName,//file name
            (data) => {
                //console.log('download data: ' + JSON.stringify(data));
                if (callback && typeof callback === "function") {
                    callback(data);
                }
                this._handleEvent(data);
            }
        );
    }

    //smbCancelDownload
    cancelDownload(downloadId) {
        RNSmb.SMB1CancelDownload(this.clientId, downloadId);
    }

    //smbUpload
    upload(fromPath, toPath, fileName, callback) {
        if (!this.uploadProgressListener) {
            this.uploadProgressListener = DeviceEventEmitter.addListener('SMBUploadProgress', this._handleEvent.bind(this));
        }
        let uploadId = Math.floor((1 + Math.random()) * 0x10000).toString(16).substring(1);

        RNSmb.SMB1Upload(
            this.clientId,
            uploadId,
            fromPath, //a path in device storage
            toPath,//a path in smb server side
            fileName,//file name
            (data) => {
                //console.log('upload data: ' + JSON.stringify(data));
                if (callback && typeof callback === "function") {
                    callback(data);
                }
                this._handleEvent(data);
            }
        );
    }

    //smbCancelUpload
    cancelUpload(uploadId) {
        RNSmb.SMB1CancelUpload(this.clientId, uploadId);
    }

    //smbRename
    rename(path, oldName, newName, callback) {
        RNSmb.SMB1Rename(
            this.clientId,
            path,//file path in smb server
            oldName,//old file name
            newName,//new file name
            (data) => {
                //console.log('rename data: ' + JSON.stringify(data));
                if (callback && typeof callback === "function") {
                    callback(data);
                }
                this._handleEvent(data);
            }
        );
    }

    //smbMoveTo
    moveTo(fromPath, toPath, fileName, callback) {
        RNSmb.SMB1MoveTo(
            this.clientId,
            fromPath,//file path in smb server
            toPath,//old file name
            fileName,//new file name
            (data) => {
                //console.log('moveTo data: ' + JSON.stringify(data));
                if (callback && typeof callback === "function") {
                    callback(data);
                }
                this._handleEvent(data);
            }
        );
    }

    //smbCopyTo
    copyTo(fromPath, toPath, fileName, callback) {
        RNSmb.SMB1CopyTo(
            this.clientId,
            fromPath,//file path in smb server
            toPath,//old file name in smb server
            fileName,//new file name
            (data) => {
                //console.log('copyTo data: ' + JSON.stringify(data));
                if (callback && typeof callback === "function") {
                    callback(data);
                }
                this._handleEvent(data);
            }
        );
    }

    //smbMkdir
    makeDir(newPath, callback) {
        RNSmb.SMB1MakeDir(
            this.clientId,
            newPath,// path of new directory in smb server
            (data) => {
                //console.log('makeDir data: ' + JSON.stringify(data));
                if (callback && typeof callback === "function") {
                    callback(data);
                }
                this._handleEvent(data);
            }
        );
    }

    //smbRm
    delete(path, callback) {
        RNSmb.SMB1Delete(
            this.clientId,
            path,// path of a file or directory in smb server that must delete
            (data) => {
                //console.log('delete data: ' + JSON.stringify(data));
                if (callback && typeof callback === "function") {
                    callback(data);
                }
                this._handleEvent(data);
            }
        );
    }

    //smbDisconnect
    disconnect(callback) {
        if (this.downloadProgressListener) {
            this.downloadProgressListener.remove();
            this.downloadProgressListener = null;
        }
        if (this.uploadProgressListener) {
            this.uploadProgressListener.remove();
            this.uploadProgressListener = null;
        }
        RNSmb.SMB1Disconnect(
            this.clientId,
            (data) => {
                //console.log('disconnect data: ' + JSON.stringify(data));
                if (callback && typeof callback === "function") {
                    callback(data);
                }
                this._handleEvent(data);
            }
        );
    }
}


export {SMB1Client};
export default SMBClient;
