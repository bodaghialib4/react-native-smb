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

        this.initOptions(callback);
    }


    _handleEvent(event) {
        let eventName = "anEventName";
        if (event && !event.success) {
            eventName = "error"
        } else {
            eventName = event.name
        }

        if (this.handlers.hasOwnProperty(eventName) && this.clientId === event.clientId) {
            this.handlers[eventName](event);
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
        RNSmb.init(this.clientId, options,
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
        RNSmb.testConnection(this.clientId,
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
        RNSmb.list(this.clientId, path, (data) => {
            //console.log('list data: ' + JSON.stringify(data));
            if (callback && typeof callback === "function") {
                callback(data);
            }
            this._handleEvent(data);
        });
    }

}


export default SMBClient;
