package com.healthsaas.zewapulseox.zewa.ble;

import android.annotation.SuppressLint;
import android.bluetooth.BluetoothAdapter;
import android.content.Context;
import android.content.Intent;
import android.os.Binder;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.SystemClock;
import android.util.Log;

import com.healthsaas.edge.safe.SafeSDK;
import com.healthsaas.zewapulseox.Constants;
import com.healthsaas.zewapulseox.Utils;
import com.healthsaas.zewapulseox.ZewaPulseOxManager;
import com.healthsaas.zewapulseox.zewa.dataModels.deviceObject;
import com.healthsaas.zewapulseox.zewa.dataModels.payloadObject;
import com.healthsaas.zewapulseox.zewa.pulseox.PulseOxSettings;
import com.healthsaas.zewapulseox.zewa.pulseox.PulseOxTransferState;
import com.healthsaas.zewapulseox.zewa.pulseox.SDKConstants;
import com.healthsaas.zewapulseox.zewa.pulseox.rawEvents;
import com.healthsaas.zewapulseox.zewadb.BtDeviceDb;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;


import java.util.ArrayList;
import java.util.Timer;
import java.util.TimerTask;

import static com.healthsaas.zewapulseox.Utils.getISO8601;

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

public class PulseOxManager extends PulseOxManagerService {
    private static final String TAG = PulseOxManager.class.getSimpleName();


    private static final long MAX_IDLE_TIME = 60 * 1000; // wait for up to 60 seconds...

    Context mContext;
    PulseOxTransferState mTransferState;
    rawEvents mTransferData;

    private Handler mHandler = new Handler();
    private boolean mRetryConnect;
    private int mMaxConnectRetries = 3;
    private static final Object sOnStartLock = new Object();
    private Timer mStopTimer = new Timer();

    private int mConnectRetries = 0;
    private int maxPayloadSize = 72;
    boolean mServiceStarted = false;
    int mDeviceSyncIndex = 0;
    boolean mSyncCycleRunning = false;
    ArrayList<deviceObject> mDevices;
    BtDeviceDb mBtDb;
    boolean firstReading = false;
    boolean fingerRemoved = false;
    int readingCount = 0;

    public PulseOxManager() {
        setModelUUIDS("PulseOx.V0");
    }

    private void setModelUUIDS(String model) {
        if (model.equals("PulseOx.V0")) {
            BLE_SERVICE_UUID = SDKConstants.PULSEOX_SERVICE_UUID;
            BLE_DATA_UUID = SDKConstants.PULSEOX_DATA_UUID;
        }
    }

    //
    // service stuff
    //
    private final IBinder mBinder = new LocalBinder();

    public class LocalBinder extends Binder {
        public PulseOxManager getService() {
            // Return this instance of LocalService so clients can call public methods
            return PulseOxManager.this;
        }
    }

    @Override
    public IBinder onBind(Intent intent) {
        return mBinder;
    }

    @Override
    public boolean onUnbind(Intent intent) {
        return super.onUnbind(intent);
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        synchronized (sOnStartLock) {
            Log.i(TAG, "Service Started.");

            mServiceStarted = true;
        }
        return START_NOT_STICKY;
    }

    @Override
    public void onCreate() {
        super.onCreate();
        mContext = getApplicationContext();
        mTransferData = new rawEvents();
        mTransferData.clearEvents();
        mDevices = new ArrayList<>();
        firstReading = false;
		fingerRemoved = false;

        maxPayloadSize = PulseOxSettings.getMaxPayloadSize();

        mBtDb = ZewaPulseOxManager.getMgrInstance().getBtDb();
        mBtDb.getAllDevices(mDevices);
        if (super.initialize(mPulseOxManagerServiceCallback))
            Log.i(TAG, "onCreate: super initialized");
        Log.i(TAG, "Service Created.");

    }

    @Override
    public void onDestroy() {
        close();
        Log.i(TAG, "Service Destroyed.");
        super.onDestroy();
    }

    private void resetStopTimer() {

        mStopTimer.cancel();
        mStopTimer.purge();
        mStopTimer = new Timer();
        mStopTimer.schedule(new stopDataReceiveService(), MAX_IDLE_TIME);
    }

    class stopDataReceiveService extends TimerTask {

        stopDataReceiveService() {

        }

        @Override
        public void run() {
            Log.i(TAG, "Stop Timer Fired!");
            finishDeviceSync("INTERRUPTED");
        }
    }

    public void startDeviceSync(String btMac) {
        deviceObject dev = new deviceObject();
        for (deviceObject d : mDevices) {
            if (d.macAddress.equals(btMac))
                dev = d;
        }
        mDevices.clear();
        mDevices.add(dev);
        startDeviceSync();
    }

    @SuppressLint("MissingPermission")
    public void startDeviceSync() {
        BluetoothAdapter lBluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
        if (mDevices.size() == 0) {
            Log.e(TAG, "There is no measuring device configured.");
            finishDeviceSync("NO_DEVICES");
            return;
        }
        if (lBluetoothAdapter != null && !lBluetoothAdapter.isEnabled()) {
            finishDeviceSync("NO_BLUETOOTH");
            return;
        }
        if (mStopTimer.purge() == 0)
            mStopTimer = new Timer();
        mStopTimer.schedule(new stopDataReceiveService(), MAX_IDLE_TIME);

        mSyncCycleRunning = false;
        mConnectRetries = 0;
        startDeviceCycle();
    }

    private void startDeviceCycle() {
        resetStopTimer();
        Log.i(TAG, "requesting disconnect");
        disconnect();
        mHandler.postDelayed(doStartDeviceCycle, 750);
    }

    @Override
    protected void updateReadBlocksState(BLEReadBlocksState newState) {
        BLEReadBlocksState lastState = mReadBlocksState;

        // update state
        super.updateReadBlocksState(newState);

        // manage transfer state
        if (lastState == BLEReadBlocksState.BLE_READ_BLOCKS_ENABLE && mReadBlocksState == BLEReadBlocksState.BLE_READ_BLOCKS_OK) {
            if (mTransferState == PulseOxTransferState.PULSEOX_TRANSFER_READ || mTransferState == PulseOxTransferState.PULSEOX_TRANSFER_RETRY) {
//                if (transferCycles <= 0 || endOfLog) {
//                    didCompleteMedWellTransfer(transferCycles);
//                    finishDeviceCycle();
//                } else {
//                    mTransferState = PulseOxTransferState.PULSEOX_TRANSFER_READ;
//                    readBlock(logIndex);
//                }
                didCompletePulseOxTransfer();
                finishDeviceCycle();
            }
        } else if (lastState == BLEReadBlocksState.BLE_READ_BLOCKS_ENABLE && mReadBlocksState == BLEReadBlocksState.BLE_READ_BLOCKS_TIMEOUT) {
//            if (mTransferState == PulseOxTransferState.PULSEOX_TRANSFER_READ) {
//                mTransferState = PulseOxTransferState.PULSEOX_TRANSFER_RETRY;
//                readBlock(logIndex);
//            } else if (mTransferState == PulseOxTransferState.PULSEOX_TRANSFER_RETRY) {
//                // failed read operation
//                didCompleteMedWellTransfer(transferCycles);
//                finishDeviceCycle();
//            }
            didCompletePulseOxTransfer();
            finishDeviceCycle();
        }
    }

    @Override
    protected void processBlock(byte[] data) {
        resetStopTimer();
        if(data[0] == -127){
            int pulse =  data[1] & 0xFF;
            int oxygen =  data[2] & 0xFF;
            int pi =  data[3] & 0xFF;
            double piv = (double) pi * 10 / 100;
            if ((pulse == 255 || oxygen == 127 || pi == 0) && firstReading) {
                Log.i(TAG, "processBlock:  Finger Removed");
                if (!fingerRemoved) {
                    fingerRemoved = true;
                    disconnect();
                    didCompletePulseOxTransfer();
                    finishDeviceCycle();
                }
            } else if (pulse > 200 || oxygen > 100 || pulse < 10 || oxygen < 25 || pi == 0) {
                Log.i(TAG, "processBlock:  Waiting for readings to settle.");
                readingCount = 0;
            } else {
                if (++readingCount >= 5) { // only take every 5th reading...
                    readingCount = 0;
                    mTransferData.createEventAndInsert(pulse, System.currentTimeMillis() / 1000, oxygen, pi);
                    if (!firstReading) {
                        firstReading = true;
                        fingerRemoved = false;
                        insertReadingIntoDb(); // send the first reading now, the rest in bulk...
                        mTransferData.clearEvents();
                    }
                    if (mTransferData.getEventCount() >= maxPayloadSize) {
                        insertReadingIntoDb();
                        mTransferData.clearEvents();
                    }
                    Log.i(TAG, "processBlock: PI: " + String.valueOf(piv));
                }
            }
        }
    }

    private Runnable doStartDeviceCycle = new Runnable() {
        @Override
        public void run() {
            _doStartDeviceCycle();
        }
    };

    private void _doStartDeviceCycle() {
        if (!mSyncCycleRunning) {
            mSyncCycleRunning = true;
            deviceObject dev = mDevices.get(mDeviceSyncIndex);
            Log.i(TAG + ".startDeviceCycle", dev.modelName + "." + dev.serialNumber);
            resetStopTimer();
//            if (!connect(dev.macAddress)) {
//                finishDeviceCycle();
//            }
            connect(dev.macAddress);
        }
    }

    private void finishDeviceCycle() {
        resetStopTimer();
        mHandler.postDelayed(doFinishDeviceCycle, 6000);
    }

    private Runnable doFinishDeviceCycle = new Runnable() {
        @Override
        public void run() {
            _doFinishDeviceCycle();
        }
    };

    private void _doFinishDeviceCycle() {
        deviceObject dev = mDevices.get(mDeviceSyncIndex);
        Log.i(TAG + ".finishDeviceCycle", dev.modelName + "." + dev.serialNumber + " outstandingSyncCount==" + dev.outstandingSyncCount);
        if (mSyncCycleRunning) {
            mSyncCycleRunning = false;
            if (mRetryConnect) {
                startDeviceCycle();
            } else {
                mDeviceSyncIndex++;
                mConnectRetries = 0;
                if (mDeviceSyncIndex < mDevices.size())
                    startDeviceCycle();
                else {
                    finishDeviceSync("COMPLETE");
                }
            }
        }
    }

    private void finishDeviceSync(String status) {
        mStopTimer.cancel();
        mStopTimer.purge();
        mDeviceSyncIndex = 0;

        Log.i(TAG, "PulseOx Data Cycle Complete - Status: " + status);

        ZewaPulseOxManager.sendPulseOxSyncDone();
    }

    private void didCompletePulseOxTransfer() {
        resetStopTimer();
        Log.i(TAG, "did complete PulseOx transfer");
        mTransferState = PulseOxTransferState.PULSEOX_TRANSFER_IDLE;
        insertReadingIntoDb();
        deviceObject devO = mBtDb.getDeviceBySN(mDevices.get(mDeviceSyncIndex).serialNumber); // get fresh device object data
        devO.batteryLevel = batteryLevel;
        devO.lastStatus = "Success!";
        devO.lastContact = getISO8601(System.currentTimeMillis());
        if (!devO.paired) { // first contact with this device - update portal, and save new backup.
            devO.paired = true;
        }
        mDevices.set(mDeviceSyncIndex, devO);
        mBtDb.update(devO);
    }

    private PulseOxManagerServiceCallback mPulseOxManagerServiceCallback = new PulseOxManagerServiceCallback() {

        public void didCompleteConnect(boolean success) {

            if (success) {

                Log.i(TAG, "Connected...");
                resetStopTimer();

                mRetryConnect = false;
                mConnectRetries = 0;
            } else {
                deviceObject devO = mDevices.get(mDeviceSyncIndex);
                disconnect();
                resetStopTimer();
                Log.i(TAG, String.format("Connection Failed - %b", mRetryConnect));
                devO.lastStatus = "Connection Failed";
                if (android.os.Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    mMaxConnectRetries = 6;
                }
                mRetryConnect = (mConnectRetries++ < mMaxConnectRetries);
                SystemClock.sleep(5000);
                devO.lastStatus = getISO8601(System.currentTimeMillis());
                mBtDb.update(devO);
                mDevices.set(mDeviceSyncIndex, devO);
                finishDeviceCycle();
            }
        }

        public void didConnect() {
            resetStopTimer();
        }

        public void didStartConnect() {
            resetStopTimer();
        }

        public void didDisconnect() {
            //Log.index(TAG, "disconnected...");
            resetStopTimer();
        }

    };

    private void insertReadingIntoDb() {
        int eventCount = mTransferData.getEventCount();
        if (eventCount == 0) {
            return;
        }
        deviceObject dev = mDevices.get(mDeviceSyncIndex);

        Gson gson = new GsonBuilder().disableHtmlEscaping().create();

        payloadObject po = new payloadObject();
        po.dt = Constants.PULSEOX_DATA_TYPE;

        po.hi.IMEI = SafeSDK.getInstance().getIMEI();
        po.hi.SN = SafeSDK.getInstance().getSerialNumber();

        po.d.rt = Utils.fmtISO8601_receiptTime();
        //po.d.ba = String.valueOf(batteryLevel);
        po.d.man = Constants.ZEWA_MFG_NAME;
        po.d.mod = Constants.ZEWA_MODEL_PULSEOX;
        po.d.sn = dev.serialNumber;
        po.d.u = "%";
        // TODO: respect max payload sizes...
        po.d.BPM = gson.toJsonTree(mTransferData.getPulses().toArray(), int[].class).toString();
        po.d.mt  = gson.toJsonTree(mTransferData.getEventTimeStamps().toArray(), long[].class).toString();
        po.d.spo2 = gson.toJsonTree(mTransferData.getOxygens().toArray(), int[].class).toString();
        po.d.PI = gson.toJsonTree(mTransferData.getPIs().toArray(), int[].class).toString();

        String x = gson.toJson(po);
        //Log.i(TAG, x);

        String endpointURL = String.format("%s%s", PulseOxSettings.getRootURL(), "sppost.svc/ga");
        boolean bInserted = SafeSDK.getInstance().insertData(x, "Content-type:application/json;charset=UTF-8", endpointURL);
        if (bInserted) {
            Log.d(TAG, "Inserted " + Constants.PULSEOX_DATA_TYPE);
        } else {
            Log.e(TAG, "Failed to insert " + Constants.PULSEOX_DATA_TYPE);
        }

        mTransferData.clearEvents();
        SafeSDK.getInstance().drainSafe();
    }
}

