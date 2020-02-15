package com.healthsaas.edge.safe;


import android.content.Context;
import android.content.Intent;
import android.os.SystemClock;
import android.util.Log;

import java.util.Date;

public class SafeSDK {
    private static SafeSDK sSafeSDK = null;
    private static final String TAG = "SafeSDK";
    private static String URL = "https://demo.ourconnectedhealth.com/ws/austonio/sppost.svc/ga";
    private static int MaxPayloadSize = 4096;
    private static String safeStatus = "Ready";
    private static long safeStatusTime = System.currentTimeMillis();
    private static int pairedDevices = 0;
    static boolean isLauncher = false;
    private String IMEI = "";
    private String serialNumber = "";
    private SafeSDK() {
    }

    public static synchronized SafeSDK getInstance() {
        if (sSafeSDK == null) {
            sSafeSDK = new SafeSDK();
            //safeStatus = "=No Devices\nConfigured=";
            URL = AppSettings.getRootURL() + "sppost.svc/ga";
            MaxPayloadSize = AppSettings.getMaxPayloadSize();
        }
        return sSafeSDK;
    }

    public synchronized Context getAppContext() {
        return SafeApp.sAppContext.getApplicationContext();
    }

    public void setIsLauncher() {
        isLauncher = true;
    }

    public boolean insertData(String payload, String headers, String url) {
        if (payload.length() <= MaxPayloadSize) {
            setStatus("Data Received");
            return SafeApp.sSafeService.insertPayload(payload, headers, url);
        }
        return  false;
    }

    public boolean addDevice(String model, String serialNumber) {
        Log.d(TAG, "addDevice: " + model + " - " + serialNumber);
        return true;
    }

    public void notifyDeviceCount(int nbrDevices) {
        pairedDevices += nbrDevices;
        if (pairedDevices > 0)
            statusUpdate("Paired Devices = " + String.valueOf(pairedDevices), null, null);
    }

    public int getDeviceCount() {
        return pairedDevices;
    }

    public boolean updateDevice(String model, String serialNumber, Date lastContact) {
        Log.d(TAG, "updateDevice: " + model + " - " + serialNumber + " - " + lastContact.toString());
        return true;
    }

    public boolean removeDevice(String model, String serialNumber) {
        Log.d(TAG, "removeDevice: " + model + " - " + serialNumber);
        return true;
    }


    public boolean statusUpdate(String var1, String var2, String var3) {
        setStatus(var1);
        return true;
    }

    public String getStatus() {
        return safeStatus;
    }

    public long getStatusTime() {
        return safeStatusTime;
    }

    public void setStatus(String status) {
        safeStatus = status;
        safeStatusTime = System.currentTimeMillis();
        if (!isLauncher)
            sendStatusUpdateMsg();
    }

    public void setIMEI(String IMEI) {
        this.IMEI = IMEI;
    }
    public String getIMEI() {
        return this.IMEI;
    }

    public void setSerialNumber(String serialNumber) {
        this.serialNumber = serialNumber;
    }
    public String getSerialNumber() {
        return this.serialNumber;
    }

    public void drainSafe() {
        //setStatus("Sending Data");
        SafeApp.sSafeService.drainSafe();
    }

    private void sendStatusUpdateMsg() {
        Intent intent = new Intent();
        intent.setAction("com.healthsaas.safe.STATUS_UPDATE");
        intent.setPackage("net.healthsaas.edge");  // required for 8.0+
        intent.putExtra("STATUS", safeStatus);
        intent.putExtra("timestamp", System.currentTimeMillis());
        intent.setFlags(Intent.FLAG_FROM_BACKGROUND | Intent.FLAG_RECEIVER_FOREGROUND);
        getAppContext().sendOrderedBroadcast(intent, null);
        SystemClock.sleep(250);
    }

}
