package com.healthsaas.zewamedwell.zewa.dataModels;


/**
 * Created by Alan on 7/11/2015.
 * local storage of paired devices
 */
public class deviceObject {
    public String modelName;
    public String serialNumber;
    public String macAddress;
    public String passKey;
    public String lastContact;
    public String lastStatus;
    public int batteryLevel;
    public String deviceID;
    public String userID;
    public String userPIN;
    public int deviceNumber;
    public boolean paired;
    public String rootURL;
    public String extraInfoJson;
    public String timeZoneOffset;
    public int outstandingSyncCount;
    public int sortVer;
    public long sortUpdatedTime;
    public long sortSyncedTime;

    public deviceObject() {
        this("","","","",0);
    }

    public deviceObject(String modelName, String serialNumber, String macAddress, String passKey, int deviceNumber) {
        this.modelName = modelName;
        this.serialNumber = serialNumber;
        this.macAddress = macAddress;
        this.passKey = passKey;
        this.lastContact = "N/A";
        this.lastStatus = "N/A";
        this.batteryLevel = -1;
        this.deviceID = "";
        this.userID = "";
        this.userPIN = "";
        this.deviceNumber = deviceNumber;
        this.paired = false;
        this.rootURL = "";
        this.extraInfoJson = "";
        this.timeZoneOffset = "";
        this.outstandingSyncCount = 1;
        this.sortVer = 0;
        this.sortUpdatedTime = -1;
        this.sortSyncedTime = -1;
    }

}
