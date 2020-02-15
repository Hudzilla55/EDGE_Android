package com.healthsaas.zewals;

import com.lifesense.ble.bean.constant.DeviceTypeConstants;
import com.lifesense.ble.bean.LsDeviceInfo;
import com.lifesense.ble.bean.constant.DeviceType;

import java.util.HashMap;

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

public class SearchDevice {
    private final String model;
    private final String bt_mac;
    private final String serialNo;
    private final DeviceType deviceType;

    private LsDeviceInfo lsDeviceInfo;

    public SearchDevice(String model, String bt_mac, DeviceType deviceType) {
        this(model, bt_mac, null, deviceType);
    }

    public SearchDevice(String model, String bt_mac, String serialNo, DeviceType deviceType) {
        this.model = model;
        this.bt_mac = bt_mac;
        this.serialNo = serialNo;
        this.deviceType = deviceType;
    }

    public String getModel() {
        return this.model;
    }

    public String getSerialNo() {
        return this.serialNo;
    }

    public String getBt_mac() {
        return this.bt_mac;
    }

    public DeviceType getDeviceType() {
        return this.deviceType;
    }

    public void setLsDeviceInfo(LsDeviceInfo lsDeviceInfo) {
        this.lsDeviceInfo = lsDeviceInfo;
    }

    public String getDeviceConstant() {
        return getDeviceConstant(this.deviceType);
    }

    private static final HashMap<DeviceType, String> deviceTypeToDeviceConstant;

    static {
        deviceTypeToDeviceConstant = new HashMap<>();
        deviceTypeToDeviceConstant.put(DeviceType.SPHYGMOMANOMETER, DeviceTypeConstants.SPHYGMOMAN_METER);
        deviceTypeToDeviceConstant.put(DeviceType.WEIGHT_SCALE, DeviceTypeConstants.WEIGHT_SCALE);
        deviceTypeToDeviceConstant.put(DeviceType.PEDOMETER, DeviceTypeConstants.PEDOMETER);
        deviceTypeToDeviceConstant.put(DeviceType.FAT_SCALE, DeviceTypeConstants.FAT_SCALE);
        deviceTypeToDeviceConstant.put(DeviceType.KITCHEN_SCALE, DeviceTypeConstants.KITCHEN_SCALE);
        deviceTypeToDeviceConstant.put(DeviceType.HEIGHT_RULER, DeviceTypeConstants.HEIGHT_RULER);
        deviceTypeToDeviceConstant.put(DeviceType.UNKNOWN, DeviceTypeConstants.UNKNOW);
    }

    public static String getDeviceConstant(DeviceType deviceType) {
        String ret_DeviceTypeConstant;
        if (deviceType == null) {
            ret_DeviceTypeConstant = deviceTypeToDeviceConstant.get(DeviceType.UNKNOWN);
        } else if (deviceTypeToDeviceConstant.containsKey(deviceType)) {
            ret_DeviceTypeConstant = deviceTypeToDeviceConstant.get(deviceType);
        } else {
            ret_DeviceTypeConstant = deviceTypeToDeviceConstant.get(DeviceType.UNKNOWN);
        }
        return ret_DeviceTypeConstant;
    }
}
