package com.healthsaas.zewamedwell;

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
import com.healthsaas.zewamedwell.zewa.ble.MedWellScannerService;
import com.healthsaas.zewamedwell.zewa.ble.MedWellScannerServiceCallback;
import com.healthsaas.zewamedwell.zewa.ble.MedWellManager;
import com.healthsaas.zewamedwell.zewa.dataModels.deviceObject;
import com.healthsaas.zewamedwell.zewa.medwell.MedWellSettings;
import com.healthsaas.zewamedwell.zewadb.BtDeviceDb;

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
public class ZewaMedWellManager extends Service {
    private static final String TAG = ZewaMedWellManager.class.getSimpleName();

    static final String ACTION_START_MEDWELL_MGR = BuildConfig.APPLICATION_ID + ".StartMedWellManager";
    static final String ACTION_STOP_MEDWELL_MGR = BuildConfig.APPLICATION_ID + ".StopMedWellManager";
    static final String ACTION_NFC_EVENT = BuildConfig.APPLICATION_ID + ".NfcEvent";
    static final String ACTION_SCAN_EVENT = BuildConfig.APPLICATION_ID + ".BleScanEvent";


    static final int MSG_TMO = 7;
//    static final int MSG_CANCEL = 8;
//    static final int MSG_PATIENT_ID = 9;

    static final int MSG_MEDWELL_SEARCH = 20;
    static final int MSG_MEDWELL_SEARCH_DONE = 21;
    static final int MSG_MEDWELL_SYNC_START = 22;
    static final int MSG_MEDWELL_SYNC_DONE = 23;
    static final int MSG_MEDWELL_UNPAIR = 27;
    static final int MSG_MEDWELL_NEW_DATA_SEARCH = 28;
    static final int MSG_MEDWELL_NEW_DATA_SEARCH_DONE = 29;
    static final int MSG_MEDWELL_INIT = 30;
    static final int MSG_MEDWELL_MANAGER_REBOOT = 31;

    public static final String KEY_CMD_ID = "CMD_ID";
    public static final String KEY_MODEL_NAME = "MODEL";
    public static final String KEY_BT_MAC = "BT_MAC";
//    public static final String KEY_BT_PIN = "BT_PIN";
    public static final String KEY_MSG_TIME = "MSG_TIME";
    public static final String KEY_SN = "DEV_SN";

    private String mNewDataDevMAC = "";

    private BtDeviceDb mBtDb;

    private static ZewaMedWellManager mgrInstance = null;

    private final IBinder mBinder = new LocalBinder();

    private Handler mgrHandler;

    private ArrayList<deviceObject> mScannedDevices;
    private ArrayList<deviceObject> mDevices;

    private boolean isMedWellSyncing = false;
    private boolean scanAggressive = false;

    private long start_search_time = 0;
    private long last_search_msg_time = 0;

    private enum BleState {
        INIT,
        IDLE,
        SEARCH_MEDWELL,
        SEARCH_MEDWELL_DONE,
        SEARCH_MEDWELL_NEW_DATA,
        SEARCH_MEDWELL_NEW_DATA_DONE,
        DATA_MEDWELL
    }

    private BleState mMwState = BleState.INIT;

    public static ZewaMedWellManager getMgrInstance() {
        return mgrInstance;
    }

    public class LocalBinder extends Binder {
        @SuppressWarnings("unused")
        public ZewaMedWellManager getService() {
            return ZewaMedWellManager.this;
        }
    }

    public BtDeviceDb getBtDb() {
        return mBtDb;
    }

    private MedWellScannerService mMedWellScannerService = null;

    private ServiceConnection mMedWellScannerServiceConnection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName componentName, IBinder iBinder) {
            mMedWellScannerService = ((MedWellScannerService.LocalBinder) iBinder).getService();
            Log.d(TAG, "MedWellScannerService connected");
            ArrayList<String> deviceMacs = new ArrayList<>();
            for (deviceObject dev : mDevices) {
                deviceMacs.add(dev.macAddress);
            }
            if (mMedWellScannerService.initialize(mMedWellScannerServiceCB, deviceMacs)) {
                Log.i(TAG, "Scanner Service initialized");
            }
        }

        @Override
        public void onServiceDisconnected(ComponentName componentName) {
            mMedWellScannerService = null;
            Log.d(TAG, "MedWellScannerService disconnected");
        }
    };

    private MedWellManager mMedWellManager = null;

    private ServiceConnection mMedWellManagerServiceConnection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName componentName, IBinder iBinder) {
            mMedWellManager = ((MedWellManager.LocalBinder) iBinder).getService();
            Log.d(TAG, "MedWellManagerService connected");
            if (mNewDataDevMAC.isEmpty()) {
                mMedWellManager.startDeviceSync();
            } else {
                mMedWellManager.startDeviceSync(mNewDataDevMAC);
            }
            mNewDataDevMAC = "";
        }

        @Override
        public void onServiceDisconnected(ComponentName componentName) {
            mMedWellManager = null;
            Log.d(TAG, "MedWellManagerService disconnected");
        }
    };

    @Override
    public void onCreate() {
        super.onCreate();
        mgrInstance = this;
        scanAggressive = MedWellSettings.getScanAggressive();
        Log.d(TAG, "onCreate");

        mScannedDevices = new ArrayList<>();
        mDevices = new ArrayList<>();

        HandlerThread mThread = new HandlerThread(Constants.MGR_NAME, Process.THREAD_PRIORITY_BACKGROUND);
        mThread.start();
        Looper mgrLooper = mThread.getLooper();
        mgrHandler = new MedWellMgrHandler(mgrLooper);

        Notification mNotification = new NotificationCompat.Builder(this, Constants.MGR_NAME)
                .setSmallIcon(R.drawable.hs_icon)
                .setContentTitle(Constants.MGR_NAME)
                .setContentText(Constants.MGR_RUNNING).build();

        startForeground(Constants.MGR_NOTIFY_ID, mNotification);


        // MedWell
        sendMedWellInit();

    }

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
            if (ZewaMedWellManager.ACTION_NFC_EVENT.equals(action)) {
                cancelTMO();
                Bundle bundle = intent.getExtras();
                handleNfcEvent(bundle);
            } else if (ZewaMedWellManager.ACTION_SCAN_EVENT.equals(action)) {
                final boolean reBootFlag = intent.getBooleanExtra("reBootFlag", false);
                final String sender = intent.getStringExtra("TAG");
                if (reBootFlag) {
                    Log.d(TAG, "onStartCommand: " + action + " from: " + sender + " recycling app");
                    mgrHandler.obtainMessage(MSG_MEDWELL_MANAGER_REBOOT).sendToTarget();
                }
            }
        }
        return START_STICKY;
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        Log.d(TAG, "onDestroy");
        doDestroy();
    }

    private void doDestroy() {
        cancelTMO();
        mgrHandler.getLooper().quit();
        if (mMedWellScannerService != null) {
            mMedWellScannerService.stopScan();
            mMedWellScannerService.stopNewDataScan();
            unbindService(mMedWellScannerServiceConnection);
            mMedWellScannerService = null;
        }
        if (mMedWellManager != null) {
            mMedWellManager.disconnect();
            unbindService(mMedWellManagerServiceConnection);
            mMedWellManager = null;
        }
        if (mBtDb != null)
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
        int cmd_id = bundle.getInt(ZewaMedWellManager.KEY_CMD_ID);
        String model = bundle.getString(ZewaMedWellManager.KEY_MODEL_NAME);
        String bt_mac = bundle.getString(ZewaMedWellManager.KEY_BT_MAC);

        switch (cmd_id) {
            case Constants.CMD_BT_PAIR:
                if (Constants.ZEWA_MODEL_MEDWELL.equalsIgnoreCase(model)) {
                    sendSearchMedWell(model, bt_mac);
                } else {
                    Log.d(TAG, "Unsupported model");
                }
                break;
            case Constants.CMD_BT_UNPAIR:
                if (Constants.ZEWA_MODEL_MEDWELL.equalsIgnoreCase(model)) {
                    // bt_mac really has MedWell serialNumber (which is bt_mac in reverse)
                    sendBtUnpairMedWell(bt_mac);
                } else {
                    Log.d(TAG, "Unsupported model");
                }
                break;
            case Constants.CMD_BT_UNPAIR_ALL:
                if (Constants.ZEWA_MODEL_MEDWELL.equalsIgnoreCase(model)) {
                    // bt_mac really has MedWell serialNumber (which is bt_mac in reverse)
                    sendBtUnpairMedWell("ALL");
                } else {
                    Log.d(TAG, "Unsupported model");
                }
                break;
            default:
                Log.d(TAG, "Unhandled cmd_id=" + String.valueOf(cmd_id));
        }
    }

    public class MedWellMgrHandler extends Handler {
        private MedWellMgrHandler(Looper looper) {
            super(looper);
        }

        @Override
        public void handleMessage(Message msg) {

            switch (msg.what) {

                case MSG_TMO:
                    handleTmoMsg(msg);
                    break;

                case MSG_MEDWELL_SEARCH:
                    handleMedWellSearchMsg(msg);
                    break;

                case MSG_MEDWELL_SEARCH_DONE:
                    handleMedWellSearchDoneMsg(msg);
                    break;

                case MSG_MEDWELL_SYNC_START:
                    handleMedWellSyncStartMsg(msg);
                    break;

                case MSG_MEDWELL_SYNC_DONE:
                    handleMedWellSyncDoneMsg();
                    break;

                case MSG_MEDWELL_UNPAIR:
                    handleMedWellUnpairMsg(msg);
                    break;

                case MSG_MEDWELL_NEW_DATA_SEARCH:
                    handleMedWellSearchNewDataMsg(msg);
                    break;

                case MSG_MEDWELL_NEW_DATA_SEARCH_DONE:
                    handleMedWellSearchNewDataDoneMsg(msg);
                    break;

                case MSG_MEDWELL_INIT:
                    handleMedWellInit();
                    break;

                case MSG_MEDWELL_MANAGER_REBOOT:
                    doDestroy();
                    ZewaMedWellExceptionHandler.recycleApp(-1);
                    break;

                default:
                    Log.d(TAG, "Unimplemented msg_id=" + String.valueOf(msg.what));
                    break;
            }
        }

        private void handleMedWellInit() {
            checkBluetooth();

            mMwState = BleState.IDLE;

            mBtDb = new BtDeviceDb(ZewaMedWellApp.getAppContext());
            mBtDb.open();
            restoreMedWellDevices();

            Intent intent = new Intent(mgrInstance.getApplicationContext(), MedWellScannerService.class);
            bindService(intent, mMedWellScannerServiceConnection, Context.BIND_AUTO_CREATE);

            SystemClock.sleep(Constants.STATE_TRANSITION_TIME);
            if ( ! mDevices.isEmpty() ) {
                sendMedWellNewDataSearch();
            }
        }

        private void handleTmoMsg(Message msg) {
            boolean restartMWNDS = false;
            int tmo_type = msg.arg1;
            switch( tmo_type) {
                case MSG_MEDWELL_SEARCH:
                    restartMWNDS = true;
                    Log.d(TAG, "Zewa search timeout for MedWell");
                    SafeSDK.getInstance().statusUpdate(Constants.BT_STATUS_PAIR_NOT_FOUND, "", Constants.ZEWA_MODEL_MEDWELL);
                    setMwBleStateIdle();
                    break;

                case MSG_MEDWELL_NEW_DATA_SEARCH:
                    restartMWNDS = true;
                    //Log.d(TAG, "Zewa search timeout for MedWell New Data");
                    setMwBleStateIdle();
                    // sleep then start new search...
                    SystemClock.sleep(Constants.TMO_MEDWELL_NEW_DATA_SEARCH_PAUSE);
                    break;

                case MSG_MEDWELL_SYNC_START:
                    restartMWNDS = true;
                    Log.e(TAG, "MedWell sync timeout");
                    isMedWellSyncing = false;
                    setMwBleStateIdle();
                    break;

                default:
                    Log.e(TAG, "Unknown tmo_type: " + String.valueOf(tmo_type));
                    break;
            }

            if (restartMWNDS) {
                // restart new data search if paired devices.
                if (!mDevices.isEmpty()) {
                    sendMedWellNewDataSearch();
                }
            }
        }

        private void handleMedWellSearchMsg(Message msg) {
            long msg_time = getMsgTime(msg);
            if (msg_time == last_search_msg_time) {
                Log.d(TAG, "Duplicate msg_time");
                return;
            }
            if (mMwState == BleState.DATA_MEDWELL) {
                Log.d(TAG, "Currently syncing, ignore new search");
                return;
            }
            last_search_msg_time = msg_time;
            if (mMwState == BleState.SEARCH_MEDWELL) {
                Log.d(TAG, "Already searching, ignore new search");
                return;
            }
            cancelTMO();
            setMwBleStateIdle();
            mMwState = BleState.SEARCH_MEDWELL;
            // will turn when connected to MedWell Scanner service

//            Bundle bundle = msg.getData();
//            String model = bundle.getString((KEY_MODEL_NAME));
//            String bt_mac = bundle.getString(KEY_BT_MAC);

            mScannedDevices.clear();
            copyDeviceObjects(mDevices, mScannedDevices);
            if( mMedWellScannerService == null) {
                Intent intent = new Intent(mgrInstance.getApplicationContext(), MedWellScannerService.class);
                bindService(intent, mMedWellScannerServiceConnection, Context.BIND_AUTO_CREATE);
            }

            mMwState = BleState.SEARCH_MEDWELL;
            start_search_time = System.currentTimeMillis();
            mMedWellScannerService.scanForDevicePeripherals();
            SafeSDK.getInstance().statusUpdate(Constants.BT_STATUS_SEARCHING, "", Constants.ZEWA_MODEL_MEDWELL);

            sendTMOinMs(MSG_MEDWELL_SEARCH, Constants.TMO_MEDWELL_SEARCH);
        }

        private void handleMedWellSearchDoneMsg(Message msg) {
            cancelTMO();
            mMwState = BleState.IDLE;
            String devMac = "";
            if (msg.obj != null) {
                deviceObject dev = (deviceObject) msg.obj;
                devMac = dev.macAddress;
            }
            if (devMac.isEmpty()) {
                // scanning error.
                sendMedWellNewDataSearch();
                return;
            }
            mDevices.clear();
            // mScannedDevices includes previously loaded devices from prefs
            copyDeviceObjects(mScannedDevices, mDevices);

            if( !mDevices.isEmpty() ) {
                MedWellReminderGetTask medwellReminderRun = new MedWellReminderGetTask(devMac);
                medwellReminderRun.start(); // get reminders for new device...
                if (mMedWellScannerService.isNotScanning())
                    sendMedWellSync(devMac, 5000); // sync new device only
            }
        }

        private void handleMedWellSearchNewDataMsg(Message msg) {
            long msg_time = getMsgTime(msg);
            if (msg_time == last_search_msg_time) {
                Log.d(TAG, "Duplicate msg_time");
                return;
            }
            last_search_msg_time = msg_time;

            if (mMwState == BleState.SEARCH_MEDWELL_NEW_DATA) {
                Log.d(TAG, "Already searching, ignore new search"); // set a timeout just in case...
                if (!scanAggressive) // aggressive scanning will not stop scanning until a device with data has been found.
                    sendTMOinMs(MSG_MEDWELL_NEW_DATA_SEARCH, Constants.TMO_MEDWELL_NEW_DATA_SEARCH);
                return;
            }
            if (isMedWellSyncing) {
                Log.d(TAG, "Data syncing, ignore new search");
                return;
            }
            setMwBleStateIdle();

            if( mMedWellScannerService == null) {
                Intent intent = new Intent(mgrInstance.getApplicationContext(), MedWellScannerService.class);
                bindService(intent, mMedWellScannerServiceConnection, Context.BIND_AUTO_CREATE);
            }

            mMwState = BleState.SEARCH_MEDWELL_NEW_DATA;
            start_search_time = System.currentTimeMillis();
            mMedWellScannerService.scanForNewData();

            if (!scanAggressive) // aggressive scanning will not stop scanning until a device with data has been found.
                sendTMOinMs(MSG_MEDWELL_NEW_DATA_SEARCH, Constants.TMO_MEDWELL_NEW_DATA_SEARCH);

        }

        private void handleMedWellSearchNewDataDoneMsg(Message msg) {
            mMwState = BleState.IDLE;

            String devMac = (String)msg.obj;

            if(devMac.isEmpty()) {
                if (!mDevices.isEmpty())
                    sendMedWellNewDataSearch();
            } else {
                MedWellReminderGetTask medwellReminderRun = new MedWellReminderGetTask(devMac);
                medwellReminderRun.start(); // get reminders for new device...
                if (mMedWellScannerService.isNotScanning())
                    sendMedWellSync(devMac, 5000); // sync new device only
            }
        }

        private void handleMedWellSyncStartMsg(Message msg) {
            mMwState = BleState.DATA_MEDWELL;
            isMedWellSyncing = true;

            if (mMedWellManager == null) {
                mNewDataDevMAC = (String)msg.obj;
                Intent intent = new Intent(mgrInstance.getApplicationContext(), MedWellManager.class);
                bindService(intent, mMedWellManagerServiceConnection, BIND_AUTO_CREATE);
            }
            sendTMOinMs(MSG_MEDWELL_SYNC_START, Constants.TMO_MEDWELL_SYNC);
        }

        private void handleMedWellSyncDoneMsg() {
            mMwState = BleState.IDLE;
            if( mMedWellManager != null) {
                unbindService(mMedWellManagerServiceConnection);
                mMedWellManager = null;
            }
            isMedWellSyncing = false;
            //sendMedWellSync(Constants.MEDWELL_DATA_SYNC_INTERVAL); TODO: update this to become a 'heart beat' connection...
            sendMedWellNewDataSearch();
            SafeSDK.getInstance().drainSafe();
        }

        private void handleMedWellUnpairMsg(Message msg) {

            Bundle bundle = msg.getData();
            String serialNumber = bundle.getString(KEY_SN);

            if (mDevices.isEmpty()) {
                Log.d(TAG, "No MedWell devices to Unpair");
                SafeSDK.getInstance().statusUpdate(Constants.BT_STATUS_UNPAIR_NOT_FOUND, serialNumber, Constants.ZEWA_MODEL_MEDWELL);
                Log.d(TAG, String.format("%s sn=%s not found to unpair", Constants.ZEWA_MODEL_MEDWELL, serialNumber));
                return;
            }
            setMwBleStateIdle();
            if (serialNumber != null) {
                if (serialNumber.equals("ALL")) {
                    for (int x = mDevices.size() -1; x >= 0; x--)
                        unpairMedWell(mDevices.get(x));
                } else if (!serialNumber.isEmpty()) {
                    unpairMedWell(findMedWellBySerialNumber(serialNumber));
                } else {
                    unpairMedWell(mDevices.get(0));
                }
            } else {
                unpairMedWell(mDevices.get(0));
            }
            if (!mDevices.isEmpty())
                sendMedWellNewDataSearch();
        }
    }

    deviceObject findMedWellBySerialNumber(final String serialNumber) {
        deviceObject ret_dev = null;
        for(deviceObject dev : mDevices) {
            if( serialNumber.equalsIgnoreCase(dev.serialNumber)) {
                ret_dev = dev;
                break;
            }
        }
        return ret_dev;
    }

    private void unpairMedWell(deviceObject dev) {
        if (dev != null) {
            removeMedWellDevice(dev);
            SafeSDK.getInstance().statusUpdate(Constants.BT_STATUS_UNPAIR_OK, dev.serialNumber, dev.modelName);
            Log.d(TAG, String.format("%s sn=%s removed", dev.modelName, dev.serialNumber));
        }
    }

    public void sendMedWellInit() {
        Message msg = mgrHandler.obtainMessage(MSG_MEDWELL_INIT);
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

    private void sendBtUnpairMedWell(final String serialNumber) {
        Message msg = mgrHandler.obtainMessage(MSG_MEDWELL_UNPAIR);
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

    public void sendSearchMedWell(final String model, final String bt_mac) {
        cancelTMO();
        Message msg = mgrHandler.obtainMessage(MSG_MEDWELL_SEARCH);
        setMsgTime(msg);
        sendBtMsg(msg, model, bt_mac);
    }

    public void sendMedWellSearchDone(final deviceObject dev) {
        cancelTMO();
        Message msg = mgrHandler.obtainMessage(MSG_MEDWELL_SEARCH_DONE, 0, 0, dev);
        setMsgTime(msg);
        mgrHandler.sendMessageDelayed(msg,750);
    }

    public void sendMedWellNewDataSearch() {
        cancelTMO();
        Message msg = mgrHandler.obtainMessage(MSG_MEDWELL_NEW_DATA_SEARCH);
        setMsgTime(msg);
        mgrHandler.sendMessageDelayed(msg,750);
    }

    public void sendMedWellNewDataSearchDone(String devMac) {
        cancelTMO();
        Message msg = mgrHandler.obtainMessage(MSG_MEDWELL_NEW_DATA_SEARCH_DONE, 0, 0, devMac);
        setMsgTime(msg);
        mgrHandler.sendMessageDelayed(msg,750);
     }

    public void sendMedWellSync(String devMac, int delayMs) {
        cancelTMO();
        if (delayMs < 750) delayMs = 750;
        Message msg = mgrHandler.obtainMessage(MSG_MEDWELL_SYNC_START, 0, 0, devMac);
        setMsgTime(msg);
        mgrHandler.sendMessageDelayed(msg, delayMs);
    }

    public static void sendMedWellSyncDone() {
        ZewaMedWellManager mgr = ZewaMedWellManager.getMgrInstance();
        if ( mgr != null) {
            mgr.cancelTMO();
            Handler mgrHandler = mgr.mgrHandler;
            if( mgrHandler != null ) {
                Message msg = mgrHandler.obtainMessage(MSG_MEDWELL_SYNC_DONE, 0, 0, null);
                mgr.setMsgTime(msg);
                mgrHandler.sendMessageDelayed(msg, 750);
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

    synchronized void restoreMedWellDevices() {
        mDevices.clear();
        mScannedDevices.clear();
        mBtDb.getAllDevices(mDevices);
        mBtDb.getAllDevices(mScannedDevices);
        if (!mDevices.isEmpty()) {
            SafeSDK.getInstance().statusUpdate(Constants.BT_STATUS_PAIR_OK, "", Constants.ZEWA_MODEL_MEDWELL);
            SafeSDK.getInstance().notifyDeviceCount(mDevices.size());
            SafeSDK.getInstance().setStatus("Ready");
        }
        for (deviceObject dev : mDevices) {
            Log.i(TAG, "restoreMedWellDevices: " + dev.serialNumber);
        }
    }

    synchronized void removeMedWellDevice(final deviceObject dev) {
        mDevices.remove(dev);
        mScannedDevices.remove(dev);
        mMedWellScannerService.mDeviceMacs.remove(dev.macAddress);
        mBtDb.deleteBySN(dev.serialNumber);
    }

    synchronized void insertMedWellDevice(final deviceObject dev) {
        mScannedDevices.remove(dev);
        mScannedDevices.add(dev);
        mBtDb.deleteBySN(dev.serialNumber);
        mBtDb.insert(dev);
    }

    // MedWell scan callback
    private MedWellScannerServiceCallback mMedWellScannerServiceCB = new MedWellScannerServiceCallback() {
        @Override
        public void didDiscoverPeripheral(int deviceIndex) {
            // check if stopping service and got a late call
            if ( mMedWellScannerService == null) {
                return;
            }
            Log.d(TAG, "didDiscoverPeripheral index=" + String.valueOf(deviceIndex));
            boolean isKnown = isKnownDevice(mMedWellScannerService.getDeviceAddress(deviceIndex));
            mMwState = BleState.SEARCH_MEDWELL_DONE;

            deviceObject discoveredDevice = new deviceObject(
                    mMedWellScannerService.getDeviceName(deviceIndex),
                    mMedWellScannerService.getDeviceSerialNumber(deviceIndex),
                    mMedWellScannerService.getDeviceAddress(deviceIndex),
                    "", mDevices.size() - (isKnown ? 1 : 0));
            insertMedWellDevice(discoveredDevice);
            long delta_time = System.currentTimeMillis() - start_search_time;
            Log.d(TAG, String.format("%s %s in %d ms", Constants.ZEWA_MODEL_MEDWELL, Constants.BT_STATUS_PAIR_OK, delta_time));
            cancelTMO();
            SafeSDK.getInstance().addDevice(Constants.ZEWA_MODEL_MEDWELL, "");
            SafeSDK.getInstance().statusUpdate(Constants.BT_STATUS_PAIR_OK, discoveredDevice.macAddress, Constants.ZEWA_MODEL_MEDWELL);
            SystemClock.sleep(Constants.STATE_TRANSITION_TIME);
            sendMedWellSearchDone(discoveredDevice);
        }
        @Override
        public void onDiscoverPeripheralError(int err) {
            // check if stopping service and got a late call
            if ( mMedWellScannerService == null) {
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
                sendMedWellSearchDone(null);
            }
        }

        @Override
        public void didDiscoverNewData(String macAddress) {
            cancelTMO();
            // check if stopping service and got a late call
            if ( mMedWellScannerService == null) {
                return;
            }
            mMwState = BleState.SEARCH_MEDWELL_NEW_DATA_DONE;

            SystemClock.sleep(Constants.STATE_TRANSITION_TIME);
            Log.d(TAG, "didDiscoverNewData MAC=" + macAddress);

            sendMedWellNewDataSearchDone(macAddress);
        }
        @Override
        public void onDiscoverNewDataError(int err) {
             // check if stopping service and got a late call
            if ( mMedWellScannerService == null) {
                return;
            }
            Log.d(TAG, "onDiscoverNewDataError =" + String.valueOf(err));
            if (err == 1) {
                if (!scanAggressive) // start a timeout just in case...
                    sendTMOinMs(MSG_MEDWELL_NEW_DATA_SEARCH, Constants.TMO_MEDWELL_NEW_DATA_SEARCH);
                return; // scan is already running, or the error says.
            }

            cancelTMO();
            mMwState = BleState.SEARCH_MEDWELL_NEW_DATA_DONE;

            SystemClock.sleep(Constants.STATE_TRANSITION_TIME);
            if (err == 2) { // cant register client interface for app
                Log.e(TAG, "onDiscoverPeripheralError: calling: sendScanCountMessage( reBootFlag: true)");
                SystemClock.sleep(5000);
                sendScanCountMessage(true);
            } else {
                // THINK ABOUT? Should i just call send MedWellNewDAtaSearch here???
                sendMedWellNewDataSearchDone("");
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

    private void setMwBleStateIdle() {
        switch (mMwState) {
            case IDLE:
                // ignore
                break;

            case SEARCH_MEDWELL:
                if (mMedWellScannerService != null) {
                    mMedWellScannerService.stopScan();
                    Log.d(TAG, "Stopping MedWell Searching");
                }
                break;

            case SEARCH_MEDWELL_DONE:
                // shouldn't happen
                // ignore
                break;

            case SEARCH_MEDWELL_NEW_DATA:
                if (mMedWellScannerService != null) {
                    mMedWellScannerService.stopNewDataScan();
                    Log.d(TAG, "Stopping MedWell New Data Searching");
                }
                break;

            case SEARCH_MEDWELL_NEW_DATA_DONE:
                // shouldn't happen
                // ignore
                break;

            case DATA_MEDWELL:
                if( mMedWellManager != null) {
                    mMedWellManager.disconnect();
                    Log.d(TAG, "Stopping MedWell Connecting");
                }
                break;

            default:
                Log.e(TAG, "Unhandled ZewaManager MwBleState=" + mMwState.toString());
                break;
        }
        if( mMwState != BleState.IDLE) {
            SystemClock.sleep(Constants.STATE_TRANSITION_TIME);
        }
        mMwState = BleState.IDLE;
    }

    private void sendScanCountMessage(boolean reBootFlag) {
        // signal other apps to interface consumption...
        Intent intent = new Intent();
        intent.setAction(Constants.BLE_SCAN_COUNT_UPDATE);
        intent.putExtra("reBootFlag", reBootFlag);
        intent.putExtra("TAG", TAG);
        ZewaMedWellApp.getAppContext().sendBroadcast(intent);
    }

    void copyDeviceObjects(final ArrayList<deviceObject> src, ArrayList<deviceObject> dest) {
        for(deviceObject dev : src) {
            dev.deviceNumber = dest.size();
            dest.add(dev);
        }
    }
}

