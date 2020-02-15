package com.healthsaas.zewamedwell.zewa.medwell;

/*---------------------------------------------------------
 *
 * HealthSaaS, Inc.
 *
 * Copyright 2018
 *
 * This file may not be copied, modified, or duplicated
 * without written permission from HealthSaaS, Inc.
 * in the terms outlined in the license provide to the
 * end user/users of this file.
 *
 ---------------------------------------------------------*/

public class MedWellDevice {
    private String modelName;
    private String serialNumber;
    private String macAddress;
    private String passKey;
    private String lastContact;
    private String lastStatus;
    private int batteryLevel;
    private String deviceID;
    private String userID;
    private String userPIN;
    private int deviceNumber;
    private boolean paired;
    private String rootURL;
    private String extraInfoJson;

    public MedWellDevice() {
        this.modelName = "";
        this.serialNumber = "";
        this.macAddress = "";
        this.passKey = "";
        this.lastContact = "N/A";
        this.lastStatus = "N/A";
        this.batteryLevel = -1;
        this.deviceID = "";
        this.userID = "";
        this.userPIN = "";
        this.deviceNumber = -1;
        this.extraInfoJson = "";
        this.paired = false;
        this.rootURL = "";

    }
}
