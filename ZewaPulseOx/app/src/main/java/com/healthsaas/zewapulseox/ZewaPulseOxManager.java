package com.healthsaas.zewapulseox;

import android.annotation.SuppressLint;
import android.annotation.TargetApi;
import android.app.Notification;
import android.app.Service;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.Binder;
import android.os.Bundle;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.IBinder;
import android.os.Looper;
import android.os.Message;
import android.os.Process;
import android.os.SystemClock;
import androidx.core.app.NotificationCompat;
import android.util.Log;

import com.healthsaas.edge.safe.SafeSDK;
import com.healthsaas.zewapulseox.zewa.ble.PulseOxScannerService;
import com.healthsaas.zewapulseox.zewa.ble.PulseOxScannerServiceCallback;
import com.healthsaas.zewapulseox.zewa.ble.PulseOxManager;
import com.healthsaas.zewapulseox.zewa.dataModels.deviceObject;
import com.healthsaas.zewapulseox.zewa.pulseox.PulseOxSettings;
import com.healthsaas.zewapulseox.zewadb.BtDeviceDb;

import java.util.ArrayList;

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

@TargetApi(23)
public class ZewaPulseOxManager extends Service {
    private static final String TAG = ZewaPulseOxManager.class.getSimpleName();

    static final String ACTION_START_PULSEOX_MGR = BuildConfig.APPLICATION_ID + ".StartPulseOxManager";
    static final String ACTION_STOP_PULSEOX_MGR = BuildConfig.APPLICATION_ID + ".StopPulseOxManager";
    static final String ACTION_NFC_EVENT = BuildConfig.APPLICATION_ID + ".NfcEvent";
    static final String ACTION_SCAN_EVENT = BuildConfig.APPLICATION_ID + ".BleScanEvent";


    static final int MSG_TMO = 7;
//    static final int MSG_CANCEL = 8;
//    static final int MSG_PATIENT_ID = 9;

    static final int MSG_PULSEOX_SEARCH = 20;
    static final int MSG_PULSEOX_SEARCH_DONE = 21;
    static final int MSG_PULSEOX_SYNC_START = 22;
    static final int MSG_PULSEOX_SYNC_DONE = 23;
    static final int MSG_PULSEOX_UNPAIR = 27;
    static final int MSG_PULSEOX_NEW_DATA_SEARCH = 28;
    static final int MSG_PULSEOX_NEW_DATA_SEARCH_DONE = 29;
    static final int MSG_PULSEOX_INIT = 30;
    static final int MSG_PULSEOX_MANAGER_REBOOT = 31;

    public static final String KEY_CMD_ID = "CMD_ID";
    public static final String KEY_MODEL_NAME = "MODEL";
    public static final String KEY_BT_MAC = "BT_MAC";
//    public static final String KEY_BT_PIN = "BT_PIN";
    public static final String KEY_MSG_TIME = "MSG_TIME";
    public static final String KEY_SN = "DEV_SN";

    private String mNewDataDevMAC = "";

    private BtDeviceDb mBtDb;

    private static ZewaPulseOxManager mgrInstance = null;

    private final IBinder mBinder = new LocalBinder();

    private Handler mgrHandler;

    private ArrayList<deviceObject> mScannedDevices;
    private ArrayList<deviceObject> mDevices;

    private boolean isPulseOxSyncing = false;
    private boolean scanAggressive = false;

    private long start_search_time = 0;
    private long last_search_msg_time = 0;

    private enum BleState {
        INIT,
        IDLE,
        SEARCH_PULSEOX,
        SEARCH_PULSEOX_DONE,
        SEARCH_PULSEOX_NEW_DATA,
        SEARCH_PULSEOX_NEW_DATA_DONE,
        DATA_PULSEOX
    }

    private BleState mPOState = BleState.INIT;

    public static ZewaPulseOxManager getMgrInstance() {
        return mgrInstance;
    }

    public class LocalBinder extends Binder {
        @SuppressWarnings("unused")
        public ZewaPulseOxManager getService() {
            return ZewaPulseOxManager.this;
        }
    }

    public BtDeviceDb getBtDb() {
        return mBtDb;
    }

    private PulseOxScannerService mPulseOxScannerService = null;

    private ServiceConnection mPulseOxScannerServiceConnection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName componentName, IBinder iBinder) {
            mPulseOxScannerService = ((PulseOxScannerService.LocalBinder) iBinder).getService();
            Log.d(TAG, "PulseOxScannerService connected");
            ArrayList<String> deviceMacs = new ArrayList<>();
            for (deviceObject dev : mDevices) {
                deviceMacs.add(dev.macAddress);
            }
            if (mPulseOxScannerService.initialize(mPulseOxScannerServiceCB, deviceMacs)) {
                Log.i(TAG, "Scanner Service initialized");
            }
        }

        @Override
        public void onServiceDisconnected(ComponentName componentName) {
            mPulseOxScannerService = null;
            Log.d(TAG, "PulseOxScannerService disconnected");
        }
    };

    private PulseOxManager mPulseOxManager = null;

    private ServiceConnection mPulseOxManagerServiceConnection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName componentName, IBinder iBinder) {
            mPulseOxManager = ((PulseOxManager.LocalBinder) iBinder).getService();
            Log.d(TAG, "PulseOxManagerService connected");
            if (mNewDataDevMAC.isEmpty()) {
                mPulseOxManager.startDeviceSync();
            } else {
                mPulseOxManager.startDeviceSync(mNewDataDevMAC);
            }
            mNewDataDevMAC = "";
        }

        @Override
        public void onServiceDisconnected(ComponentName componentName) {
            mPulseOxManager = null;
            Log.d(TAG, "PulseOxManagerService disconnected");
        }
    };

    @Override
    public void onCreate() {
        super.onCreate();
        mgrInstance = this;
        scanAggressive = PulseOxSettings.getScanAggressive();
        Log.d(TAG, "onCreate");

        mScannedDevices = new ArrayList<>();
        mDevices = new ArrayList<>();

        HandlerThread mThread = new HandlerThread(Constants.MGR_NAME, Process.THREAD_PRIORITY_BACKGROUND);
        mThread.start();
        Looper mgrLooper = mThread.getLooper();
        mgrHandler = new PulseOxMgrHandler(mgrLooper);

        Notification mNotification = new NotificationCompat.Builder(this, Constants.MGR_NAME)
                .setSmallIcon(R.drawable.hs_icon)
                .setContentTitle(Constants.MGR_NAME)
                .setContentText(Constants.MGR_RUNNING).build();

        startForeground(Constants.MGR_NOTIFY_ID, mNotification);


        // PulseOx
        sendPulseOxInit();

    }

    @SuppressLint("MissingPermission")
    void checkBluetooth() {
        final BluetoothManager btMgr = (BluetoothManager) getSystemService(Context.BLUETOOTH_SERVICE);
        final BluetoothAdapter btAdapter;
        if (btMgr != null) {
            btAdapter = btMgr.getAdapter();
            if ( ! btAdapter.isEnabled() ) {
                Log.d(TAG, "Turning on BluetoothAdapter");
                btAdapter.enable();
            }
        }
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        Log.d(TAG, "onStartCommand: " + String.valueOf(startId));
        if( intent != null ) {
            String action = intent.getAction();
            if (ZewaPulseOxManager.ACTION_NFC_EVENT.equals(action)) {
                Bundle bundle = intent.getExtras();
                handleNfcEvent(bundle);
            } else if (ZewaPulseOxManager.ACTION_SCAN_EVENT.equals(action)) {
                final boolean reBootFlag = intent.getBooleanExtra("reBootFlag", false);
                final String sender = intent.getStringExtra("TAG");
                if (reBootFlag) {
                    Log.d(TAG, "onStartCommand: " + action + " from: " + sender + " recycling app");
                    mgrHandler.obtainMessage(MSG_PULSEOX_MANAGER_REBOOT).sendToTarget();
                }
            }
        }
        return START_STICKY;
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        doDestroy();
    }

    private void doDestroy() {
        Log.d(TAG, "onDestroy");
        cancelTMO();
        mgrHandler.getLooper().quit();
        if (mPulseOxScannerService != null) {
            mPulseOxScannerService.stopScan();
            mPulseOxScannerService.stopNewDataScan();
            unbindService(mPulseOxScannerServiceConnection);
            mPulseOxScannerService = null;
        }
        if (mPulseOxManager != null) {
            mPulseOxManager.disconnect();
            unbindService(mPulseOxManagerServiceConnection);
            mPulseOxManager = null;
        }
        mBtDb.close();
        mBtDb = null;
    }

    @Override
    public IBinder onBind(Intent intent) {
        Log.d(TAG, "onBind");
        return mBinder;
    }

    private void handleNfcEvent(Bundle bundle) {
        if (bundle == null) {
            Log.e(TAG, "No args for NfcEvent");
            return;
        }
        int cmd_id = bundle.getInt(ZewaPulseOxManager.KEY_CMD_ID);
        String model = bundle.getString(ZewaPulseOxManager.KEY_MODEL_NAME);
        String bt_mac = bundle.getString(ZewaPulseOxManager.KEY_BT_MAC);

        switch (cmd_id) {
            case Constants.CMD_BT_PAIR:
                if (Constants.ZEWA_MODEL_PULSEOX.equalsIgnoreCase(model)) {
                    sendSearchPulseOx(model, bt_mac);
                } else {
                    Log.d(TAG, "Unsupported model");
                }
                break;
            case Constants.CMD_BT_UNPAIR:
                if (Constants.ZEWA_MODEL_PULSEOX.equalsIgnoreCase(model)) {
                    // bt_mac really has PulseOx serialNumber (which is bt_mac in reverse)
                    sendBtUnpairPulseOx(bt_mac);
                } else {
                    Log.d(TAG, "Unsupported model");
                }
                break;
            case Constants.CMD_BT_UNPAIR_ALL:
                if (Constants.ZEWA_MODEL_PULSEOX.equalsIgnoreCase(model)) {
                    // bt_mac really has PulseOx serialNumber (which is bt_mac in reverse)
                    sendBtUnpairPulseOx("ALL");
                } else {
                    Log.d(TAG, "Unsupported model");
                }
                break;
            default:
                Log.d(TAG, "Unhandled cmd_id=" + String.valueOf(cmd_id));
        }
    }

    public class PulseOxMgrHandler extends Handler {
        private PulseOxMgrHandler(Looper looper) {
            super(looper);
        }

        @Override
        public void handleMessage(Message msg) {

            switch (msg.what) {

                case MSG_TMO:
                    handleTmoMsg(msg);
                    break;

                case MSG_PULSEOX_SEARCH:
                    handlePulseOxSearchMsg(msg);
                    break;

                case MSG_PULSEOX_SEARCH_DONE:
                    handlePulseOxSearchDoneMsg(msg);
                    break;

                case MSG_PULSEOX_SYNC_START:
                    handlePulseOxSyncStartMsg(msg);
                    break;

                case MSG_PULSEOX_SYNC_DONE:
                    handlePulseOxSyncDoneMsg();
                    break;

                case MSG_PULSEOX_UNPAIR:
                    handlePulseOxUnpairMsg(msg);
                    break;

                case MSG_PULSEOX_NEW_DATA_SEARCH:
                    handlePulseOxSearchNewDataMsg(msg);
                    break;

                case MSG_PULSEOX_NEW_DATA_SEARCH_DONE:
                    handlePulseOxSearchNewDataDoneMsg(msg);
                    break;

                case MSG_PULSEOX_INIT:
                    handlePulseOxInit();
                    break;

                case MSG_PULSEOX_MANAGER_REBOOT:
                    doDestroy();
                    ZewaPulseOxExceptionHandler.recycleApp(-1);
                    break;

                default:
                    Log.d(TAG, "Unimplemented msg_id=" + String.valueOf(msg.what));
                    break;
            }
        }

        private void handlePulseOxInit() {
            checkBluetooth();

            mPOState = BleState.IDLE;

            mBtDb = new BtDeviceDb(mgrInstance.getApplicationContext());
            mBtDb.open();
            restorePulseOxDevices();

            Intent intent = new Intent(mgrInstance.getApplicationContext(), PulseOxScannerService.class);
            bindService(intent, mPulseOxScannerServiceConnection, Context.BIND_AUTO_CREATE);

            SystemClock.sleep(Constants.STATE_TRANSITION_TIME);
            if ( ! mDevices.isEmpty() ) {
                sendPulseOxNewDataSearch();
            }
        }

        private void handleTmoMsg(Message msg) {
            boolean restartMWNDS = false;
            int tmo_type = msg.arg1;
            switch( tmo_type) {
                case MSG_PULSEOX_SEARCH:
                    restartMWNDS = true;
                    Log.d(TAG, "Zewa search timeout for PulseOx");
                    SafeSDK.getInstance().statusUpdate(Constants.BT_STATUS_PAIR_NOT_FOUND, "", Constants.ZEWA_MODEL_PULSEOX);
                    setPOBleStateIdle();
                    break;

                case MSG_PULSEOX_NEW_DATA_SEARCH:
                    restartMWNDS = true;
                    Log.d(TAG, "Zewa search timeout for PulseOx New Data");
                    setPOBleStateIdle();
                    // sleep then start new search...
                    SystemClock.sleep(Constants.TMO_PULSEOX_NEW_DATA_SEARCH_PAUSE);
                    break;

                case MSG_PULSEOX_SYNC_START:
                    restartMWNDS = true;
                    Log.e(TAG, "PulseOx sync timeout");
                    isPulseOxSyncing = false;
                    setPOBleStateIdle();
                    break;

                default:
                    Log.e(TAG, "Unknown tmo_type: " + String.valueOf(tmo_type));
                    break;
            }

            if (restartMWNDS) {
                // restart new data search if paired devices.
                if (!mDevices.isEmpty()) {
                    sendPulseOxNewDataSearch();
                }
            }
        }

        private void handlePulseOxSearchMsg(Message msg) {
            long msg_time = getMsgTime(msg);
            if (msg_time == last_search_msg_time) {
                Log.d(TAG, "Duplicate msg_time");
                return;
            }
            if (mPOState == BleState.DATA_PULSEOX) {
                Log.d(TAG, "Currently syncing, ignore new search");
                return;
            }
            last_search_msg_time = msg_time;
            if (mPOState == BleState.SEARCH_PULSEOX) {
                Log.d(TAG, "Already searching, ignore new search");
                return;
            }
            cancelTMO();
            setPOBleStateIdle();
            mPOState = BleState.SEARCH_PULSEOX;
            // will turn when connected to PulseOx Scanner service

//            Bundle bundle = msg.getData();
//            String model = bundle.getString((KEY_MODEL_NAME));
//            String bt_mac = bundle.getString(KEY_BT_MAC);

            mScannedDevices.clear();
            copyDeviceObjects(mDevices, mScannedDevices);
            if( mPulseOxScannerService == null) {
                Intent intent = new Intent(mgrInstance.getApplicationContext(), PulseOxScannerService.class);
                bindService(intent, mPulseOxScannerServiceConnection, Context.BIND_AUTO_CREATE);
            }

            mPOState = BleState.SEARCH_PULSEOX;
            start_search_time = System.currentTimeMillis();
            mPulseOxScannerService.scanForDevicePeripherals();
            SafeSDK.getInstance().statusUpdate(Constants.BT_STATUS_SEARCHING, "", Constants.ZEWA_MODEL_PULSEOX);

            sendTMOinMs(MSG_PULSEOX_SEARCH, Constants.TMO_PULSEOX_SEARCH);
        }

        private void handlePulseOxSearchDoneMsg(Message msg) {
            cancelTMO();
            mPOState = BleState.IDLE;
            String devMac = "";
            if (msg.obj != null) {
                deviceObject dev = (deviceObject) msg.obj;
                devMac = dev.macAddress;
            }
            if (devMac.isEmpty()) {
                // scanning error.
                sendPulseOxNewDataSearch();
                return;
            }
            mDevices.clear();
            // mScannedDevices includes previously loaded devices from prefs
            copyDeviceObjects(mScannedDevices, mDevices);

            if( !mDevices.isEmpty() ) {
                if (mPulseOxScannerService.isNotScanning())
                    sendPulseOxSync(devMac, 50); // sync new device only
            }
        }

        private void handlePulseOxSearchNewDataMsg(Message msg) {
            long msg_time = getMsgTime(msg);
            if (msg_time == last_search_msg_time) {
                Log.d(TAG, "Duplicate msg_time");
                return;
            }
            last_search_msg_time = msg_time;

            if (mPOState == BleState.SEARCH_PULSEOX_NEW_DATA) {
                Log.d(TAG, "Already searching, ignore new search"); // set a timeout just in case...
                if (!scanAggressive) // aggressive scanning will not stop scanning until a device with data has been found.
                    sendTMOinMs(MSG_PULSEOX_NEW_DATA_SEARCH, Constants.TMO_PULSEOX_NEW_DATA_SEARCH);
                return;
            }
            if (isPulseOxSyncing) {
                Log.d(TAG, "Data syncing, ignore new search");
                return;
            }
            setPOBleStateIdle();

            if( mPulseOxScannerService == null) {
                Intent intent = new Intent(mgrInstance.getApplicationContext(), PulseOxScannerService.class);
                bindService(intent, mPulseOxScannerServiceConnection, Context.BIND_AUTO_CREATE);
            }

            mPOState = BleState.SEARCH_PULSEOX_NEW_DATA;
            start_search_time = System.currentTimeMillis();
            mPulseOxScannerService.scanForNewData();

            if (!scanAggressive) // aggressive scanning will not stop scanning until a device with data has been found.
                sendTMOinMs(MSG_PULSEOX_NEW_DATA_SEARCH, Constants.TMO_PULSEOX_NEW_DATA_SEARCH);

        }

        private void handlePulseOxSearchNewDataDoneMsg(Message msg) {
            mPOState = BleState.IDLE;

            String devMac = (String)msg.obj;

            if(devMac.isEmpty()) {
                if (!mDevices.isEmpty())
                    sendPulseOxNewDataSearch();
            } else {
                if (mPulseOxScannerService.isNotScanning())
                    sendPulseOxSync(devMac, 50);
            }
        }

        private void handlePulseOxSyncStartMsg(Message msg) {
            mPOState = BleState.DATA_PULSEOX;
            isPulseOxSyncing = true;

            if (mPulseOxManager == null) {
                mNewDataDevMAC = (String)msg.obj;
                Intent intent = new Intent(mgrInstance.getApplicationContext(), PulseOxManager.class);
                bindService(intent, mPulseOxManagerServiceConnection, BIND_AUTO_CREATE);
            }
            sendTMOinMs(MSG_PULSEOX_SYNC_START, Constants.TMO_PULSEOX_SYNC);
        }

        private void handlePulseOxSyncDoneMsg() {
            mPOState = BleState.IDLE;
            if( mPulseOxManager != null) {
                unbindService(mPulseOxManagerServiceConnection);
                mPulseOxManager = null;
            }
            isPulseOxSyncing = false;
            //sendPulseOxSync(Constants.PULSEOX_DATA_SYNC_INTERVAL); TODO: update this to become a 'heart beat' connection...
            sendPulseOxNewDataSearch();
        }

        private void handlePulseOxUnpairMsg(Message msg) {

            Bundle bundle = msg.getData();
            String serialNumber = bundle.getString(KEY_SN);

            if (mDevices.isEmpty()) {
                Log.d(TAG, "No PulseOx devices to Unpair");
                SafeSDK.getInstance().statusUpdate(Constants.BT_STATUS_UNPAIR_NOT_FOUND, serialNumber, Constants.ZEWA_MODEL_PULSEOX);
                Log.d(TAG, String.format("%s sn=%s not found to unpair", Constants.ZEWA_MODEL_PULSEOX, serialNumber));
                return;
            }
            setPOBleStateIdle();
            if (serialNumber != null) {
                if (serialNumber.equals("ALL")) {
                    for (int x = mDevices.size() -1; x >= 0; x--)
                        unpairPulseOx(mDevices.get(x));
                } else if (!serialNumber.isEmpty()) {
                    unpairPulseOx(findPulseOxBySerialNumber(serialNumber));
                } else {
                    unpairPulseOx(mDevices.get(0));
                }
            } else {
                unpairPulseOx(mDevices.get(0));
            }
            if (!mDevices.isEmpty())
                sendPulseOxNewDataSearch();
        }
    }

    deviceObject findPulseOxBySerialNumber(final String serialNumber) {
        deviceObject ret_dev = null;
        for(deviceObject dev : mDevices) {
            if( serialNumber.equalsIgnoreCase(dev.serialNumber)) {
                ret_dev = dev;
                break;
            }
        }
        return ret_dev;
    }

    private void unpairPulseOx(deviceObject dev) {
        if (dev != null) {
            removePulseOxDevice(dev);
            SafeSDK.getInstance().statusUpdate(Constants.BT_STATUS_UNPAIR_OK, dev.serialNumber, dev.modelName);
            Log.d(TAG, String.format("%s sn=%s removed", dev.modelName, dev.serialNumber));
        }
    }

    public void sendPulseOxInit() {
        Message msg = mgrHandler.obtainMessage(MSG_PULSEOX_INIT);
        mgrHandler.sendMessageDelayed(msg, 500);
    }

    private void sendBtMsg(Message msg, final String model, final String bt_mac) {
        Bundle bundle = new Bundle();
        bundle.putString(KEY_MODEL_NAME, model);
        bundle.putString(KEY_BT_MAC, bt_mac);
        long now = System.currentTimeMillis();
        bundle.putLong(KEY_MSG_TIME, now);
        msg.setData(bundle);
        setMsgTime(msg);
        mgrHandler.sendMessage(msg);
    }

    private void sendBtUnpairPulseOx(final String serialNumber) {
        Message msg = mgrHandler.obtainMessage(MSG_PULSEOX_UNPAIR);
        Bundle bundle = new Bundle();
        bundle.putString(KEY_SN, serialNumber);
        long now = System.currentTimeMillis();
        bundle.putLong(KEY_MSG_TIME, now);
        msg.setData(bundle);
        setMsgTime(msg);
        mgrHandler.sendMessage(msg);
    }

    private void sendTMOinMs(final int tmo_type, final int delayMs) {
        Message msg = mgrHandler.obtainMessage(MSG_TMO, tmo_type, 0, null);
        mgrHandler.sendMessageDelayed(msg, delayMs);
    }

    private void cancelTMO() {
        mgrHandler.removeMessages(MSG_TMO);
    }

    public void sendSearchPulseOx(final String model, final String bt_mac) {
        cancelTMO();
        setPOBleStateIdle();
        Message msg = mgrHandler.obtainMessage(MSG_PULSEOX_SEARCH);
        setMsgTime(msg);
        sendBtMsg(msg, model, bt_mac);
    }

    public void sendPulseOxSearchDone(final deviceObject dev) {
        cancelTMO();
        Message msg = mgrHandler.obtainMessage(MSG_PULSEOX_SEARCH_DONE, 0, 0, dev);
        setMsgTime(msg);
        mgrHandler.sendMessageDelayed(msg,250);
    }

    public void sendPulseOxNewDataSearch() {
        cancelTMO();
        Message msg = mgrHandler.obtainMessage(MSG_PULSEOX_NEW_DATA_SEARCH);
        setMsgTime(msg);
        mgrHandler.sendMessageDelayed(msg,2500);
    }

    public void sendPulseOxNewDataSearchDone(String devMac) {
        cancelTMO();
        Message msg = mgrHandler.obtainMessage(MSG_PULSEOX_NEW_DATA_SEARCH_DONE, 0, 0, devMac);
        setMsgTime(msg);
        mgrHandler.sendMessageDelayed(msg,250);
     }

    public void sendPulseOxSync(String devMac, int delayMs) {
        cancelTMO();
        if (delayMs < 250) delayMs = 250;
        Message msg = mgrHandler.obtainMessage(MSG_PULSEOX_SYNC_START, 0, 0, devMac);
        setMsgTime(msg);
        mgrHandler.sendMessageDelayed(msg, delayMs);
    }

    public static void sendPulseOxSyncDone() {
        ZewaPulseOxManager mgr = ZewaPulseOxManager.getMgrInstance();
        if ( mgr != null) {
            mgr.cancelTMO();
            Handler mgrHandler = mgr.mgrHandler;
            if( mgrHandler != null ) {
                Message msg = mgrHandler.obtainMessage(MSG_PULSEOX_SYNC_DONE, 0, 0, null);
                mgr.setMsgTime(msg);
                mgrHandler.sendMessageDelayed(msg, 6000);
            }
        }
    }

    public void setMsgTime(Message msg) {
        long now = System.currentTimeMillis();
        msg.arg1 = (int) (now >> 32);
        msg.arg2 = (int) now;
    }

    public long getMsgTime(Message msg) {
        // clear sign extension of lower 32 bits when cast to long
        return ((long) msg.arg1 << 32) | ((long) msg.arg2);
    }

    synchronized void restorePulseOxDevices() {
        mDevices.clear();
        mScannedDevices.clear();
        mBtDb.getAllDevices(mDevices);
        mBtDb.getAllDevices(mScannedDevices);
        if (!mDevices.isEmpty()) {
            SafeSDK.getInstance().statusUpdate(Constants.BT_STATUS_PAIR_OK, "", Constants.ZEWA_MODEL_PULSEOX);
            SafeSDK.getInstance().notifyDeviceCount(mDevices.size());
            SafeSDK.getInstance().setStatus("Ready");
        }
        for (deviceObject dev : mDevices) {
            Log.i(TAG, "restorePulseOxDevices: " + dev.serialNumber);
        }
    }

    synchronized void removePulseOxDevice(final deviceObject dev) {
        mDevices.remove(dev);
        mScannedDevices.remove(dev);
        mPulseOxScannerService.mDeviceMacs.remove(dev.macAddress);
        mBtDb.deleteBySN(dev.serialNumber);
    }

    synchronized void insertPulseOxDevice(final deviceObject dev) {
        mScannedDevices.add(dev);
        mBtDb.insert(dev);
    }

    // PulseOx scan callback
    private PulseOxScannerServiceCallback mPulseOxScannerServiceCB = new PulseOxScannerServiceCallback() {
        @Override
        public void didDiscoverPeripheral(int deviceIndex) {
            // check if stopping service and got a late call
            if ( mPulseOxScannerService == null) {
                return;
            }
            Log.d(TAG, "didDiscoverPeripheral index=" + String.valueOf(deviceIndex));
            if (!isKnownDevice(mPulseOxScannerService.getDeviceAddress(deviceIndex))) {
                mPOState = BleState.SEARCH_PULSEOX_DONE;

                deviceObject discoveredDevice = new deviceObject(
                        Constants.ZEWA_MODEL_PULSEOX,
                        mPulseOxScannerService.getDeviceSerialNumber(deviceIndex),
                        mPulseOxScannerService.getDeviceAddress(deviceIndex),
                        "", mDevices.size());
                insertPulseOxDevice(discoveredDevice);
                long delta_time = System.currentTimeMillis() - start_search_time;
                Log.d(TAG, String.format("%s %s in %d ms", "Zewa PulseOx", Constants.BT_STATUS_PAIR_OK, delta_time));
                // HACK: NOTE: if this device has been paired with this hub before, update not effective, so unpair first to ensure pairing state cleared...
                cancelTMO();
                SafeSDK.getInstance().statusUpdate(Constants.BT_STATUS_UNPAIR_OK, discoveredDevice.macAddress, Constants.ZEWA_MODEL_PULSEOX);
                SafeSDK.getInstance().statusUpdate(Constants.BT_STATUS_PAIR_OK, discoveredDevice.macAddress, Constants.ZEWA_MODEL_PULSEOX);
                SystemClock.sleep(Constants.STATE_TRANSITION_TIME);
                sendPulseOxSearchDone(discoveredDevice);
            }
        }
        @Override
        public void onDiscoverPeripheralError(int err) {
            // check if stopping service and got a late call
            if ( mPulseOxScannerService == null) {
                return;
            }
            Log.d(TAG, "onDiscoverPeripheralError =" + String.valueOf(err));
            if (err == 1) {
                return; // scan is already running...
            }
            cancelTMO();
            if (err == 2) { // cant register client interface for app
                Log.i(TAG, "onDiscoverPeripheralError: calling: ZewaExceptionHandler.recycleApp()");
                SystemClock.sleep(5000);
                sendScanCountMessage(true);
            } else {
                sendPulseOxSearchDone(null);
            }
        }

        @Override
        public void didDiscoverNewData(String macAddress) {
            cancelTMO();
            // check if stopping service and got a late call
            if ( mPulseOxScannerService == null) {
                return;
            }
            mPOState = BleState.SEARCH_PULSEOX_NEW_DATA_DONE;

            SystemClock.sleep(Constants.STATE_TRANSITION_TIME);
            Log.d(TAG, "didDiscoverNewData MAC=" + macAddress);

            sendPulseOxNewDataSearchDone(macAddress);
        }
        @Override
        public void onDiscoverNewDataError(int err) {
             // check if stopping service and got a late call
            if ( mPulseOxScannerService == null) {
                return;
            }
            Log.d(TAG, "onDiscoverNewDataError =" + String.valueOf(err));
            if (err == 1) {
                if (!scanAggressive) // start a timeout just in case...
                    sendTMOinMs(MSG_PULSEOX_NEW_DATA_SEARCH, Constants.TMO_PULSEOX_NEW_DATA_SEARCH);
                return; // scan is already running, or the error says.
            }

            cancelTMO();
            mPOState = BleState.SEARCH_PULSEOX_NEW_DATA_DONE;

            SystemClock.sleep(Constants.STATE_TRANSITION_TIME);
            if (err == 2) { // cant register client interface for app
                Log.e(TAG, "onDiscoverPeripheralError: calling: sendScanCountMessage( reBootFlag: true)");
                SystemClock.sleep(5000);
                sendScanCountMessage(true);
            } else {
                // THINK ABOUT? Should i just call send PulseOxNewDAtaSearch here???
                sendPulseOxNewDataSearchDone("");
            }
        }
    };

    private boolean isKnownDevice(final String macAddress) {
        if (macAddress == null) {
            return true; // null mac address - should never happen, but return true so no further processing for this device should happen.
        } else {
            for (int i = 0; i < mScannedDevices.size(); i++) {
                if (mScannedDevices.get(i).macAddress.equals(macAddress))
                    return true;
            }
        }
        return false;
    }

    private void setPOBleStateIdle() {
        switch (mPOState) {
            case IDLE:
                // ignore
                break;

            case SEARCH_PULSEOX:
                if (mPulseOxScannerService != null) {
                    mPulseOxScannerService.stopScan();
                    Log.d(TAG, "Stopping PulseOx Searching");
                }
                break;

            case SEARCH_PULSEOX_DONE:
                // shouldn't happen
                // ignore
                break;

            case SEARCH_PULSEOX_NEW_DATA:
                if (mPulseOxScannerService != null) {
                    mPulseOxScannerService.stopNewDataScan();
                    Log.d(TAG, "Stopping PulseOx New Data Searching");
                }
                break;

            case SEARCH_PULSEOX_NEW_DATA_DONE:
                // shouldn't happen
                // ignore
                break;

            case DATA_PULSEOX:
                if( mPulseOxManager != null) {
                    mPulseOxManager.disconnect();
                    Log.d(TAG, "Stopping PulseOx Connecting");
                }
                break;

            default:
                Log.e(TAG, "Unhandled ZewaManager POBleState=" + mPOState.toString());
                break;
        }
        if( mPOState != BleState.IDLE) {
            SystemClock.sleep(Constants.STATE_TRANSITION_TIME);
        }
        mPOState = BleState.IDLE;
    }

    private void sendScanCountMessage(boolean reBootFlag) {
        // signal other apps to interface consumption...
        Intent intent = new Intent();
        intent.setAction(Constants.BLE_SCAN_COUNT_UPDATE);
        intent.putExtra("reBootFlag", reBootFlag);
        intent.putExtra("TAG", TAG);
        ZewaPulseOxApp.getAppContext().sendBroadcast(intent);
    }

    void copyDeviceObjects(final ArrayList<deviceObject> src, ArrayList<deviceObject> dest) {
        for(deviceObject dev : src) {
            dev.deviceNumber = dest.size();
            dest.add(dev);
        }
    }
}

