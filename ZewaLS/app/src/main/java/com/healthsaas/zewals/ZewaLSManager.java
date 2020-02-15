package com.healthsaas.zewals;

import android.annotation.TargetApi;
import android.app.Notification;
import android.app.Service;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothManager;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
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
import androidx.localbroadcastmanager.content.LocalBroadcastManager;

import android.util.Log;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import com.healthsaas.edge.safe.SafeSDK;
import com.healthsaas.zewals.dataModels.ACTPayloadObject;
import com.healthsaas.zewals.dataModels.BPMPayloadObject;
import com.healthsaas.zewals.dataModels.WTPayloadObject;
import com.healthsaas.zewals.zewadb.DatabaseManager;
import com.healthsaas.zewals.zewadb.DeviceDataUtils;
import com.healthsaas.zewals.zewadb.PedoEntry;
import com.healthsaas.zewals.zewadb.PedoPayload;
import com.healthsaas.zewals.zewadb.SafeDb;
import com.lifesense.ble.LsBleManager;
import com.lifesense.ble.OnSettingListener;
import com.lifesense.ble.PairCallback;
import com.lifesense.ble.ReceiveDataCallback;
import com.lifesense.ble.SearchCallback;
import com.lifesense.ble.bean.BloodGlucoseData;
import com.lifesense.ble.bean.BloodPressureData;
import com.lifesense.ble.bean.HeartbeatData;
import com.lifesense.ble.bean.PairedConfirmInfo;
import com.lifesense.ble.bean.PedometerHeartRateData;
import com.lifesense.ble.bean.PedometerNightMode;
import com.lifesense.ble.bean.PedometerSleepData;
import com.lifesense.ble.bean.constant.DeviceConnectState;
import com.lifesense.ble.bean.constant.DeviceTypeConstants;
import com.lifesense.ble.bean.HeightData;
import com.lifesense.ble.bean.KitchenScaleData;
import com.lifesense.ble.bean.LsDeviceInfo;
import com.lifesense.ble.bean.PedometerData;
import com.lifesense.ble.bean.ScanIntervalConfig;
import com.lifesense.ble.bean.WeightData_A2;
import com.lifesense.ble.bean.WeightData_A3;
import com.lifesense.ble.bean.WeightUserInfo;
import com.lifesense.ble.bean.constant.BroadcastType;
import com.lifesense.ble.bean.constant.DeviceType;
import com.lifesense.ble.bean.constant.HourSystem;
import com.lifesense.ble.bean.constant.LengthUnit;
import com.lifesense.ble.bean.constant.OperationCommand;
import com.lifesense.ble.bean.constant.PacketProfile;
import com.lifesense.ble.bean.constant.PairedConfirmState;
import com.lifesense.ble.bean.constant.PedometerPage;
import com.lifesense.ble.bean.constant.RemoteControlCmd;

import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static com.lifesense.ble.bean.constant.DeviceConnectState.CONNECTED_SUCCESS;
import com.healthsaas.zewals.zewadb.SleepData;

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
public class ZewaLSManager extends Service {
    private static final String TAG = ZewaLSManager.class.getSimpleName();

    static final String ACTION_START_ZEWA_MGR = BuildConfig.APPLICATION_ID + ".StartZewaLSManager";
    static final String ACTION_STOP_ZEWA_MGR = BuildConfig.APPLICATION_ID + ".StopZewaLSManager";
    static final String ACTION_NFC_EVENT = BuildConfig.APPLICATION_ID + ".NfcEvent";
    static final String ACTION_SCAN_EVENT = BuildConfig.APPLICATION_ID + ".BleScanEvent";

    static final int MSG_ZEWA_INIT = 1;
    static final int MSG_ZEWA_SEARCH = 2;
    static final int MSG_ZEWA_SEARCH_DONE = 3;
    static final int MSG_ZEWA_PAIR = 4;
    static final int MSG_ZEWA_PAIR_DONE = 5;
    static final int MSG_ZEWA_UNPAIR = 6;
    static final int MSG_ZEWA_TMO = 7;
    static final int MSG_ZEWA_SYNC = 8;
    static final int MSG_ZEWA_REBOOT = 9;

    public static final String KEY_CMD_ID = "CMD_ID";
    public static final String KEY_MODEL_NAME = "MODEL";
    public static final String KEY_BT_MAC = "BT_MAC";
    public static final String KEY_BT_CODE = "BT_CODE";
    public static final String KEY_MSG_TIME = "MSG_TIME";

    private static ZewaLSManager mgrInstance = null;

    private final IBinder mBinder = new LocalBinder();

    private Handler mgrHandler;

    private SafeDb mSafeDb;

    private LsBleManager mlsBleManager;
    private LsDeviceInfo lsDevicePairing;
    private static final HashMap<String, DeviceType> deviceTypeModelMap;

    static {
        deviceTypeModelMap = new HashMap<>();
        deviceTypeModelMap.put(Constants.ZEWA_MODEL_BPM_GENERIC, DeviceType.SPHYGMOMANOMETER);
        deviceTypeModelMap.put(Constants.ZEWA_MODEL_BPM_1, DeviceType.SPHYGMOMANOMETER);
        deviceTypeModelMap.put(Constants.ZEWA_MODEL_BPM_2, DeviceType.SPHYGMOMANOMETER);
        deviceTypeModelMap.put(Constants.ZEWA_MODEL_BPM_3, DeviceType.SPHYGMOMANOMETER);
        deviceTypeModelMap.put(Constants.ZEWA_MODEL_WS_GENERIC, DeviceType.WEIGHT_SCALE);
        deviceTypeModelMap.put(Constants.ZEWA_MODEL_WS_1, DeviceType.WEIGHT_SCALE);
        deviceTypeModelMap.put(Constants.ZEWA_MODEL_ACTRKR_GENERIC, DeviceType.PEDOMETER);
        deviceTypeModelMap.put(Constants.ZEWA_MODEL_ACTRKR_1, DeviceType.PEDOMETER);
    }

    private static final HashMap<String, DeviceType> deviceTypeStringMap;

    static {
        deviceTypeStringMap = new HashMap<>();
        deviceTypeStringMap.put(DeviceTypeConstants.SPHYGMOMAN_METER, DeviceType.SPHYGMOMANOMETER);
        deviceTypeStringMap.put(DeviceTypeConstants.WEIGHT_SCALE, DeviceType.WEIGHT_SCALE);
        deviceTypeStringMap.put(DeviceTypeConstants.PEDOMETER, DeviceType.PEDOMETER);
        deviceTypeStringMap.put(DeviceTypeConstants.FAT_SCALE, DeviceType.FAT_SCALE);
        deviceTypeStringMap.put(DeviceTypeConstants.KITCHEN_SCALE, DeviceType.KITCHEN_SCALE);
        deviceTypeStringMap.put(DeviceTypeConstants.HEIGHT_RULER, DeviceType.HEIGHT_RULER);
        deviceTypeStringMap.put(DeviceTypeConstants.UNKNOW, DeviceType.UNKNOWN);
    }

    long connectTime = 0;
    int connections = 0;
    private List<DeviceType> mScanDeviceType;
    private SearchDevice mSearchDevice;
    private BroadcastType mBroadcastType = BroadcastType.ALL;
    private HashMap<String, LsDeviceInfo> mBtMacPairedDeviceMap;
    private HashMap<String, LsDeviceInfo> mBroadcastIdPairedDeviceMap;

    private long start_pair_time = 0;
    private long last_search_msg_time = 0;
    private int maxActrkrPayloadSize = 16;

    private Gson gson = new GsonBuilder().disableHtmlEscaping().create();

    private enum BleState {
        INIT,
        IDLE,
        SEARCH_ZEWA,
        SEARCH_ZEWA_DONE,
        PAIR_ZEWA,
        PAIR_ZEWA_DONE,
        DATA_ZEWA
    }

    private BleState mLsState = BleState.INIT;

    @SuppressWarnings("unused")
    public static ZewaLSManager getMgrInstance() {
        return mgrInstance;
    }

    private class LocalBinder extends Binder {
        @SuppressWarnings("unused")
        public ZewaLSManager getService() {
            return ZewaLSManager.this;
        }
    }

    @Override
    public void onCreate() {
        super.onCreate();
        mgrInstance = this;
        Log.d(TAG, "onCreate");

        mScanDeviceType = new ArrayList<>();

        HandlerThread mThread = new HandlerThread(Constants.MGR_NAME, Process.THREAD_PRIORITY_BACKGROUND);
        mThread.start();
        Looper mgrLooper;
        mgrLooper = mThread.getLooper();
        mgrHandler = new ZewaMgrHandler(mgrLooper);

        Notification mNotification = new NotificationCompat.Builder(this, Constants.MGR_NAME)
                .setSmallIcon(R.drawable.mhclogo)
                .setContentTitle(Constants.MGR_NAME)
                .setContentText(Constants.MGR_RUNNING).build();

        startForeground(Constants.MGR_NOTIFY_ID, mNotification);

        maxActrkrPayloadSize = ZewaLSSettings.getMaxPayloadSize();

        mSafeDb = new SafeDb(mgrInstance.getApplicationContext());
        mSafeDb.open();

        // LifeSense
        sendZewaInit();

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

    void checkStartDataCollection() {
        if (mBtMacPairedDeviceMap != null && ! mBtMacPairedDeviceMap.isEmpty()) {
            if( mlsBleManager != null ) {
                boolean lsDRSStarted = mlsBleManager.startDataReceiveService(mReceiveDataCallback);
                mlsBleManager.registerDataSyncCallback(mReceiveDataCallback);
                if (lsDRSStarted) {
                    Log.d(TAG, "Start Zewa Data Collection true");
                    mLsState = BleState.DATA_ZEWA;
                } else {
                    Log.e(TAG, "Failed to Start Zewa Data Collection false");
                    sendTMOinMs(MSG_ZEWA_SYNC, 500, null);
                }
                SystemClock.sleep(Constants.STATE_TRANSITION_TIME);
            }
        }
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        Log.d(TAG, "onStartCommand: " + startId);
        if( intent != null ) {
            String action = intent.getAction();
            if (ZewaLSManager.ACTION_NFC_EVENT.equals(action)) {
                Bundle bundle = intent.getExtras();
                handleNfcEvent(bundle);
            } else if (ZewaLSManager.ACTION_SCAN_EVENT.equals(action)) {
                final boolean reBootFlag = intent.getBooleanExtra("reBootFlag", false);
                final String sender = intent.getStringExtra("TAG");
                if (reBootFlag) {
                    Log.d(TAG, "onStartCommand: " + action + " from: " + sender + " recycling app");
                    mgrHandler.obtainMessage(MSG_ZEWA_REBOOT).sendToTarget();
                }
            }
        }
        return START_STICKY;
    }

    @Override
    public void onDestroy() {
        Log.d(TAG, "onDestroy");
        mgrHandler.getLooper().quit();
        if (mSafeDb != null) {
            mSafeDb.close();
            mSafeDb = null;
        }
        if (mlsBleManager != null) {
            mlsBleManager.stopSearch();
            mlsBleManager.stopDataReceiveService();
            mlsBleManager = null;
        }
        super.onDestroy();
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
        int cmd_id = bundle.getInt(ZewaLSManager.KEY_CMD_ID);
        String model = bundle.getString(ZewaLSManager.KEY_MODEL_NAME);
        if (model == null) model = "";
        String bt_mac = bundle.getString(ZewaLSManager.KEY_BT_MAC);

        switch (cmd_id) {
            case Constants.CMD_BT_PAIR:
                if (model.startsWith(Constants.ZEWA_MFG_NAME)) {
                    sendBtSearchZewa(model, bt_mac);
                } else {
                    Log.d(TAG, "Unsupported model");
                }
                break;
            case Constants.CMD_BT_PAIR_CANCEL:
                cancelZewaTMO();
                mLsState = BleState.IDLE;
                mlsBleManager.cancelDevicePairing(lsDevicePairing);
                SafeSDK.getInstance().statusUpdate(Constants.BT_STATUS_SEARCHING_CANCEL, "", mSearchDevice.getModel());
                if (!mBtMacPairedDeviceMap.isEmpty()) {
                    checkStartDataCollection();
                }
                break;
            case Constants.CMD_BT_PAIR_CODE:
                break;
            case Constants.CMD_BT_UNPAIR:
                if (model.startsWith(Constants.ZEWA_MFG_NAME)) {
                    sendBtUnpairZewa(model, bt_mac);
                } else {
                    Log.d(TAG, "Unsupported model");
                }
                break;
            case Constants.CMD_BT_UNPAIR_ALL:
                if (model.startsWith(Constants.ZEWA_MFG_NAME)) {
                    sendBtUnpairZewa("ALL", "ALL");
                } else {
                    Log.d(TAG, "Unsupported model");
                }
                break;
            default:
                Log.d(TAG, "Unhandled cmd_id=" + cmd_id);
        }
    }

    public class ZewaMgrHandler extends Handler {
        private ZewaMgrHandler(Looper looper) {
            super(looper);
        }

        @Override
        public void handleMessage(Message msg) {

            switch (msg.what) {

                case MSG_ZEWA_INIT:
                    handleZewaInit();
                    break;

                case MSG_ZEWA_SEARCH:
                    handleZewaSearchMsg(msg);
                    break;

                case MSG_ZEWA_SEARCH_DONE:
                    handleZewaSearchDoneMsg(msg);
                    break;

                case MSG_ZEWA_PAIR_DONE:
                    handleZewaPairDoneMsg(msg);
                    break;

                case MSG_ZEWA_UNPAIR:
                    handleZewaUnpairMsg(msg);
                    break;

                case MSG_ZEWA_TMO:
                    handleZewaTmoMsg(msg);
                    break;

                case MSG_ZEWA_REBOOT:
                    ZewaLSExceptionHandler.recycleApp(-1);
                    break;

                default:
                    Log.d(TAG, "Unimplemented msg_id=" + msg.what);
                    break;
            }
        }

        private void handleZewaInit() {
            checkBluetooth();

            mlsBleManager = LsBleManager.getInstance();
            if (ZewaLSSettings.getScanAggressive())
                mlsBleManager.setCustomConfig(new ScanIntervalConfig(false));
            mlsBleManager.initialize(mgrInstance.getApplicationContext());

            mLsState = BleState.IDLE;
            if( mlsBleManager.isOpenBluetooth()) {
                Log.d(TAG, "Bluetooth Ready");
            } else {
                Log.e(TAG, "Bluetooth not ready");
            }
            mlsBleManager.stopSearch();
            mlsBleManager.stopDataReceiveService();
            SystemClock.sleep(Constants.STATE_TRANSITION_TIME);

            mBtMacPairedDeviceMap = null;
            mBroadcastIdPairedDeviceMap = null;

            restorePairedDevices();
            checkStartDataCollection();
            drainSafeDb();
            }

        private void handleZewaSearchMsg(final Message msg) {
            long msg_time = getMsgTime(msg);
            Log.d(TAG, "handleZewaSearchMsg: msg_time=" + msg_time);
            if (msg_time == last_search_msg_time) {
                Log.d(TAG, "Duplicate msg_time");
                return;
            }
            last_search_msg_time = msg_time;

            if (!mlsBleManager.isSupportLowEnergy()) {
                Log.e(TAG, "BLE not supported");
                return;
            }
            if (!mlsBleManager.isOpenBluetooth()) {
                Log.e(TAG, "Bluetooth not on");
                return;
            }

            if (mLsState == BleState.SEARCH_ZEWA) {
                Log.d(TAG, "Already searching, ignore new search");
                return;
            }
            if (mLsState == BleState.PAIR_ZEWA) {
                Log.d(TAG, "Already pairing, ignore new search");
                return;
            }
            if (mLsState == BleState.DATA_ZEWA) {
                setLsBleStateIdle();
            }

            final Bundle bundle = msg.getData();
            final String model = bundle.getString((KEY_MODEL_NAME));
            final String bt_mac = bundle.getString(KEY_BT_MAC);
            final DeviceType deviceType = getDeviceTypeOfModel(model);
            mSearchDevice = new SearchDevice(model, bt_mac, deviceType);
            mScanDeviceType.clear();
            mScanDeviceType.add(deviceType);
            sendTMOinMs(MSG_ZEWA_SEARCH, Constants.TMO_ZEWA_SEARCH, mSearchDevice);
            if (mlsBleManager.searchLsDevice(mSearchCallback, getDeviceTypes(), getBroadcastType())) {
                SafeSDK.getInstance().statusUpdate(Constants.BT_STATUS_SEARCHING, "", model);
                mLsState = BleState.SEARCH_ZEWA;
//                isScanning = true;
                Log.i(TAG, "handleZewaSearchMsg: Started new device search");
            } else {
                Log.e(TAG, "handleZewaSearchMsg: Failed to start new device search");
            }
        }

        private void handleZewaSearchDoneMsg(Message msg) {
            long msg_time = getMsgTime(msg);
            Log.d(TAG, "Zewa Search Done msg @ " + msg_time);
            mLsState = BleState.IDLE;

            if (msg.obj instanceof LsDeviceInfo) {
                LsDeviceInfo lsDeviceInfo = (LsDeviceInfo) msg.obj;
                mLsState = BleState.PAIR_ZEWA;
                sendTMOinMs(MSG_ZEWA_PAIR, Constants.TMO_ZEWA_PAIR, lsDeviceInfo);
                start_pair_time = System.currentTimeMillis();
                Log.d(TAG, "Zewa Pairing start @ " + start_pair_time);
                lsDevicePairing = lsDeviceInfo;
                mlsBleManager.pairingWithDevice(lsDeviceInfo, mPairCallback);
            } else {
                Log.e(TAG, "No LsDeviceInfo to pair");
                sendTMOinMs(MSG_ZEWA_PAIR, 0, null);
            }
        }

        private void handleZewaPairDoneMsg(Message msg) {
            long msg_time = getMsgTime(msg);
            Log.d(TAG, "Zewa Pair Done Msg @ " + msg_time);
            mLsState = BleState.IDLE;

            if (msg.obj instanceof LsDeviceInfo) {
                LsDeviceInfo lsDeviceInfo = (LsDeviceInfo) msg.obj;

                LsDeviceInfo pairedDeviceInfo = addPairedDevice(lsDeviceInfo);
                // delete first, since its Broadcast id may have changed
                if (pairedDeviceInfo != null) {
                    mlsBleManager.deleteMeasureDevice(pairedDeviceInfo.getBroadcastID());
                }
                mlsBleManager.addMeasureDevice(lsDeviceInfo);
                savePairedDevices();
                mlsBleManager.cancelDevicePairing(lsDevicePairing); // ADDED 12/06 got stuck in pairing mode after pairing...
            }
            if (!mBtMacPairedDeviceMap.isEmpty()) {
                checkStartDataCollection();
            }
        }

        private void handleZewaUnpairMsg(Message msg) {
            Bundle bundle = msg.getData();
            String model = bundle.getString(KEY_MODEL_NAME);
            String bt_mac = bundle.getString(KEY_BT_MAC);
            DeviceType deviceType = getDeviceTypeOfModel(model);

            LsDeviceInfo lsDeviceInfo = null;
            if (bt_mac != null && !bt_mac.isEmpty()) {
                if (bt_mac.equals("ALL")) {
                    for (int x = mBtMacPairedDeviceMap.size() -1; x >= 0; x--) {
                        lsDeviceInfo = (LsDeviceInfo) mBtMacPairedDeviceMap.values().toArray()[x];
                        unpairDevice(model, bt_mac, lsDeviceInfo);
                    }
                } else {
                    lsDeviceInfo = mBtMacPairedDeviceMap.get(bt_mac);
                    if (lsDeviceInfo==null) {
                        // try to reverse the octet's
                        lsDeviceInfo = mBtMacPairedDeviceMap.get(reverseMAC(bt_mac));
                    }
                    unpairDevice(model, bt_mac, lsDeviceInfo);
                }
           } else {
                for (Object o : mBtMacPairedDeviceMap.entrySet()) {
                    Map.Entry pair = (Map.Entry) o;
                    lsDeviceInfo = (LsDeviceInfo) pair.getValue();
                    if (getDeviceTypeOfString(lsDeviceInfo.getDeviceType()).equals(deviceType)) {
                        break;
                    }
                    lsDeviceInfo = null;
                }
                unpairDevice(model, bt_mac, lsDeviceInfo);
            }
            if (mLsState == BleState.DATA_ZEWA) {
                checkStartDataCollection();
            } else {
                mLsState = BleState.IDLE;
            }
        }

        private void handleZewaTmoMsg(Message msg) {
            boolean restartLSData = false;
            int tmo_type = msg.arg1;
            switch( tmo_type) {
                case MSG_ZEWA_SEARCH:
                    restartLSData = true;
                    Log.d(TAG, "Zewa search timeout for " + mSearchDevice.getModel());
                    setLsBleStateIdle();
                    SafeSDK.getInstance().statusUpdate(Constants.BT_STATUS_PAIR_NOT_FOUND, "", mSearchDevice.getModel());
                    break;

                case MSG_ZEWA_PAIR:
                    restartLSData = true;
                    Log.d(TAG, "Zewa pair timeout for " + mSearchDevice.getModel());
                    setLsBleStateIdle();
                    SafeSDK.getInstance().statusUpdate(Constants.BT_STATUS_PAIR_FAIL, "", mSearchDevice.getModel());
                    break;

                case MSG_ZEWA_SYNC:
                    restartLSData = true;
                    Log.d(TAG, "Zewa sync fail timeout");
                    setLsBleStateIdle();
                    break;

                default:
                    Log.e(TAG, "Unknown tmo_type: " + tmo_type);
                    break;
            }

            if (restartLSData) {
                // restart data sync if have already paired devices
                if (!mBtMacPairedDeviceMap.isEmpty()) {
                    Log.e(TAG, "handleZewaTmoMsg: calling checkStartDataCollection");
                    checkStartDataCollection();
                }
            }
        }
    }

    private void unpairDevice(String model, String bt_mac, LsDeviceInfo lsDeviceInfo) {
        if (lsDeviceInfo != null) {
            if (mLsState == BleState.DATA_ZEWA) {
                mlsBleManager.stopDataReceiveService();
                SystemClock.sleep(Constants.STATE_TRANSITION_TIME);
            }
            removePairedDevice(lsDeviceInfo);
            savePairedDevices();
            mlsBleManager.deleteMeasureDevice(lsDeviceInfo.getBroadcastID());
            SafeSDK.getInstance().statusUpdate(Constants.BT_STATUS_UNPAIR_OK, lsDeviceInfo.getMacAddress(), model);
        } else {
            SafeSDK.getInstance().statusUpdate(Constants.BT_STATUS_UNPAIR_NOT_FOUND, bt_mac, model);
        }
    }
    private String reverseMAC(String mac) {
        StringBuilder resp = new StringBuilder();
        mac = mac.replace(":", "");
        for (int x = mac.length(); x > 0; x -= 2) {
            resp.append(mac.substring(x-2, x));
            if (x > 2)
                resp.append(":");
        }
        return  resp.toString();
    }

    public void sendZewaInit() {
        Message msg = mgrHandler.obtainMessage(MSG_ZEWA_INIT);
        Log.i(TAG, "sendZewaInit: ");
        mgrHandler.sendMessageDelayed(msg, 500);
    }

    public void sendBtSearchZewa(final String model, final String bt_mac) {
        Message msg = mgrHandler.obtainMessage(MSG_ZEWA_SEARCH);
        setMsgTime(msg);
        sendBtMsg(msg, model, bt_mac);
    }

    public void sendBtUnpairZewa(final String model, final String bt_mac) {
        Message msg = mgrHandler.obtainMessage(MSG_ZEWA_UNPAIR);
        sendBtMsg(msg, model, bt_mac);
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

    private void sendTMOinMs(final int tmo_type, final int delayMs, final Object obj) {
        Log.d(TAG, String.format("Set TMO of %d after %d ms", tmo_type, delayMs));
        Message msg = mgrHandler.obtainMessage(MSG_ZEWA_TMO, tmo_type, 0, obj);
        mgrHandler.sendMessageDelayed(msg, delayMs);
    }

    private void cancelZewaTMO() {
        mgrHandler.removeMessages(MSG_ZEWA_TMO);
        Log.d(TAG, "cancelZewaTMO");
    }

    public void sendZewaSearchDone(final LsDeviceInfo lsDeviceInfo) {
        if (lsDeviceInfo != null) {
            Message msg = mgrHandler.obtainMessage(MSG_ZEWA_SEARCH_DONE, 0, 0, lsDeviceInfo);
            setMsgTime(msg);
            mgrHandler.sendMessage(msg);
        }
    }

    public void sendZewaPairDone(final LsDeviceInfo lsDeviceInfo) {
        if (lsDeviceInfo != null) {
            Message msg = mgrHandler.obtainMessage(MSG_ZEWA_PAIR_DONE, 0, 0, lsDeviceInfo);
            setMsgTime(msg);
            mgrHandler.sendMessage(msg);
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

    // ls devices.
    private SearchCallback mSearchCallback = new SearchCallback() {
        @Override
        public void onSearchResults(final LsDeviceInfo lsDevice) {
            _onSearchResults(lsDevice);
        }

        private void _onSearchResults(LsDeviceInfo lsDevice) {
            Log.d(TAG, "onSearchResults");
            if (lsDevice != null) {
                if (lsDevice.getDeviceType().equals(mSearchDevice.getDeviceConstant())) {
                    //Log.d(TAG, "onSearchResults: " + lsDevice.toString());
                    if (mSearchDevice.getBt_mac().isEmpty()
                            || mSearchDevice.getBt_mac().equals(lsDevice.getMacAddress())) {
                        //Log.d(TAG, "Found " + lsDevice.toString());
                        mLsState = BleState.SEARCH_ZEWA_DONE;
                        mlsBleManager.stopSearch();
                        cancelZewaTMO();
                        SystemClock.sleep(Constants.STATE_TRANSITION_TIME);
                        sendZewaSearchDone(lsDevice);
                    }
                }
            }
        }
    };

    private PairCallback mPairCallback = new PairCallback() {
//        private List<DeviceUserInfo> mDeviceUserList;
//
//        @Override
//        public void onDiscoverUserInfo(List userList) {
//            Log.d(TAG, "onDiscoverUserInfo");
//            if (userList != null) {
//                mDeviceUserList = (List<DeviceUserInfo>) userList;
//            }
//            if (mDeviceUserList == null) {
//                Log.d(TAG, "onDiscoverUserInfo: null");
//            }
//        }
        @Override
        public void onDeviceOperationCommandUpdate(final String macAddress,OperationCommand command, Object obj)
        {
            Log.i(TAG, "onDeviceOperationCommandUpdate: " + macAddress + " - " + command.toString() + " - " + obj);
            if(OperationCommand.CMD_RANDOM_NUMBER == command){
                showRandomNumberInputView(macAddress);
            }
            else if(OperationCommand.CMD_PAIRED_CONFIRM == command)
            {
                setPedoDefaults(macAddress);
                PairedConfirmInfo pairedConfirmInfo = new PairedConfirmInfo(PairedConfirmState.PAIRING_SUCCESS );
                pairedConfirmInfo.setUserNumber(0);
                mlsBleManager.inputOperationCommand(macAddress, OperationCommand.CMD_PAIRED_CONFIRM, pairedConfirmInfo);
            }
        }

        @Override
        public void onPairResults(final LsDeviceInfo lsDevice, final int status) {
            _onPairResults(lsDevice, status);

        }

        private void _onPairResults(LsDeviceInfo lsDevice, int status) {
            Log.d(TAG, "onPairResults: " + status);
            // ignore late  results
            if (mLsState != BleState.PAIR_ZEWA) {
                Log.d(TAG, "_onPairResults: LateResults - ignoring");
                mlsBleManager.cancelDevicePairing(lsDevicePairing);
                return;
            }
            if (lsDevice != null
                    && status == 0
                    && (lsDevice.getPairStatus() == 1 || lsDevice.getRegisterStatus() == 1)
                    && lsDevice.getBroadcastID() != null
                    && !lsDevice.getBroadcastID().isEmpty()) {
                mLsState = BleState.PAIR_ZEWA_DONE;
                cancelZewaTMO();
                long delta_time = System.currentTimeMillis() - start_pair_time;
                Log.i(TAG, String.format("%s %s in %d ms", mSearchDevice.getModel(), Constants.BT_STATUS_PAIR_OK, delta_time));
                SafeSDK.getInstance().statusUpdate(Constants.BT_STATUS_UNPAIR_OK, lsDevice.getMacAddress(), mSearchDevice.getModel());
                SafeSDK.getInstance().statusUpdate(Constants.BT_STATUS_PAIR_OK, lsDevice.getMacAddress(), mSearchDevice.getModel());
                SystemClock.sleep(Constants.STATE_TRANSITION_TIME);
                sendZewaPairDone(lsDevice);
            } else {
                Log.d(TAG, "_onPairResults: failed object tests");
            }
        }
    };

    /**
     * Device Setting Listener
     */
    private OnSettingListener mSettingListener=new OnSettingListener()
    {
        @Override
        public void onFailure(int errorCode)
        {
            Log.i(TAG, "OnSettingListener.onFailure: errorCode=" + errorCode);
            //DialogUtils.showToastMessage(getActivity(),msg+",errorCode="+errorCode);
        }

        @Override
        public void onSuccess(String mac) {
            String msg=getResources().getString(R.string.setting_ok);
            Log.i(TAG, "OnSettingListener.onSuccess:" + mac);
            //DialogUtils.showToastMessage(getActivity(),msg);
        }
    };

    synchronized LsDeviceInfo addPairedDevice(LsDeviceInfo lsDeviceInfo) {

        final String btMac = lsDeviceInfo.getMacAddress();

        Log.d(TAG, "addPairedDevice: " + lsDeviceInfo.toString());
        // swap out old broadcast id (BID) & password with new ones in MAC MAP in case the device exists
        // The MAC is the only consistent/guaranteed value - BID & password change with each pairing
        LsDeviceInfo oldPairedDevice = mBtMacPairedDeviceMap.put(btMac, lsDeviceInfo);

        if (oldPairedDevice != null) { // remove an existing paired device information from BID MAP.
            mBroadcastIdPairedDeviceMap.remove(oldPairedDevice.getBroadcastID());
        }
        final String broadcastId = lsDeviceInfo.getBroadcastID();
        mBroadcastIdPairedDeviceMap.put(broadcastId, lsDeviceInfo); // add new device to BID MAP...

        // create a dummy payload...
        String endpointURL = String.format("%s%s", ZewaLSSettings.getRootURL(), "sppost.svc/ga");
        String deviceType = lsDeviceInfo.getDeviceType();

        switch (deviceType) {
            case "01":
                // Weight
                WTPayloadObject wtpo = new WTPayloadObject();
                wtpo.dt = "WEIGHT";

                wtpo.hi.IMEI = SafeSDK.getInstance().getIMEI();
                wtpo.hi.SN = SafeSDK.getInstance().getSerialNumber();

                wtpo.d.rt = Utils.fmtISO8601_receiptTime();
                wtpo.d.ba = String.valueOf(lsDeviceInfo.getBattery());
                wtpo.d.man = Constants.ZEWA_MFG_NAME;
                wtpo.d.mod = Constants.ZEWA_MODEL_WS_1;
                wtpo.d.sn = lsDeviceInfo.getDeviceId();
                wtpo.d.wt = String.valueOf(0);
                wtpo.d.u = "lb";
                wtpo.d.Kg = String.valueOf(0);
                wtpo.d.r2 = String.valueOf(0);
                wtpo.d.mt = Utils.fmtISO8601_receiptTime();

                String pox = gson.toJson(wtpo);
                SafeSDK.getInstance().insertData(pox, "Content-type:application/json;charset=UTF-8", endpointURL);

                break;
            case "04":
                // pedo
                ACTPayloadObject po = new ACTPayloadObject();
                po.dt = "ACTRKR";

                po.hi.IMEI = SafeSDK.getInstance().getIMEI();
                po.hi.SN = SafeSDK.getInstance().getSerialNumber();

                po.d.rt = String.valueOf(System.currentTimeMillis() / 1000);
                po.d.b = String.valueOf(lsDeviceInfo.getBattery());
                po.d.b2 = "0";
                po.d.man = "Zewa";
                po.d.mod = getModelForDeviceID(lsDeviceInfo.getDeviceId());
                po.d.sn = lsDeviceInfo.getDeviceId();
                po.d.mt = String.valueOf(System.currentTimeMillis() / 1000);

                po.d.ws  = "0";
                po.d.rs = "0";
                po.d.di = "0";
                po.d.et = "0";
                po.d.ca = "0";
                po.d.il = "0";
                po.d.sl = "0";
                po.d.ea = "0";
                po.d.tzo = String.valueOf(Utils.getShortDateOffsetMinutes(Utils.getISO8601(System.currentTimeMillis() / 1000)));

                String x = gson.toJson(po);
                SafeSDK.getInstance().insertData(x, "Content-type:application/json;charset=UTF-8", endpointURL);

                break;
            case "08":
                // BP
                String model;
                if (lsDeviceInfo.getDeviceName().equals("802A6") && lsDeviceInfo.getSoftwareVersion().equals("A2")) {
                    model = "Zewa UAM-910BT";
                } else {
                    model = lsDeviceInfo.getModelNumber();
                }
                if (!model.startsWith(Constants.ZEWA_MFG_NAME))
                    model = Constants.ZEWA_MFG_NAME + " " + model;

                BPMPayloadObject bppo = new BPMPayloadObject();
                bppo.dt = "BP";

                bppo.hi.IMEI = SafeSDK.getInstance().getIMEI();
                bppo.hi.SN = SafeSDK.getInstance().getSerialNumber();

                bppo.d.rt = Utils.fmtISO8601_receiptTime();
                bppo.d.ba = String.valueOf(lsDeviceInfo.getBattery());
                bppo.d.man = Constants.ZEWA_MFG_NAME;
                bppo.d.mod = model;
                bppo.d.sn = lsDeviceInfo.getDeviceId() + (model.equals(Constants.ZEWA_MODEL_BPM_3) ? "" : "-0");
                bppo.d.mt = Utils.fmtISO8601_receiptTime();
                bppo.d.u = "mmHg";
                bppo.d.sys = String.valueOf(0);
                bppo.d.dia = String.valueOf(0);
                bppo.d.p = String.valueOf(0);
                bppo.d.map = String.valueOf(0);

                String bppox = gson.toJson(bppo);
                SafeSDK.getInstance().insertData(bppox, "Content-type:application/json;charset=UTF-8", endpointURL);

                break;
        }

        return oldPairedDevice; // return old device or null so we can remove from the
        // LSManager device list if not null (if non existent oldPairedDevice will be null)
    }

    private void setPedoDefaults(String deviceMac) {

        if (mlsBleManager.checkDeviceConnectState(deviceMac)==DeviceConnectState.CONNECTED_SUCCESS) {
            Log.i(TAG, "setPedoDefaults: Connected Success");
        }
        // default pages...
        int[] pgNbrs = {0, 1, 2, 3, 4, 12, 14 }; // Clock, Step, Calorie, Distance, Heart Rate, Stop watch, Battery
        List<PedometerPage> pages = new ArrayList<PedometerPage>();
        for (int pgNbr : pgNbrs) {
            pages.add(PedometerPage.values()[pgNbr]);
        }
        mlsBleManager.updatePedometerPageSequence(deviceMac, pages, mSettingListener);

        // clock display
        HourSystem selectMode = HourSystem.values()[1]; // 0 = 24h, 1 = 12h
        mlsBleManager.updateDeviceTimeFormat(deviceMac, selectMode, mSettingListener);

        // distance units
        LengthUnit lu = LengthUnit.values()[1]; // 0 = km, 1 = mile
        mlsBleManager.updateDeviceDistanceUnit(deviceMac, lu, mSettingListener);

        // Night mode
        PedometerNightMode nightMode = new PedometerNightMode("21:00", "06:00"); // 9pm - 6am
        mlsBleManager.updatePedometerNightMode(deviceMac, true, nightMode, mSettingListener);
    }

   public void showRandomNumberInputView(final String macAddress)
    {

        Log.i(TAG, "showRandomNumberInputView: " + macAddress);
        Intent prompt = new Intent(MainActivity.PROMPT_FOR_PAIR_CODE_MSG);
        prompt.putExtra("macAddress",macAddress);
        LocalBroadcastManager.getInstance(this).sendBroadcast(prompt);
    }

    synchronized void removePairedDevice(final LsDeviceInfo lsDeviceInfo) {
        mBroadcastIdPairedDeviceMap.remove(lsDeviceInfo.getBroadcastID());
        mBtMacPairedDeviceMap.remove(lsDeviceInfo.getMacAddress());
    }

    synchronized void savePairedDevices() {
        SharedPreferences sharedPrefs = mgrInstance.getApplicationContext().getSharedPreferences(Constants.PREFS_NAME, MODE_PRIVATE);
        SharedPreferences.Editor editor = sharedPrefs.edit();
        if (!mBtMacPairedDeviceMap.isEmpty()) {
            Gson gson = new Gson();
            Type type = new TypeToken<HashMap<String, LsDeviceInfo>>() {
            }.getType();
            String json = gson.toJson(mBtMacPairedDeviceMap, type);
            editor.putString(Constants.PREFS_PAIRED_DEVICES, json);
        } else {
            editor.putString(Constants.PREFS_PAIRED_DEVICES, "");
        }
        editor.apply();
    }

    synchronized void restorePairedDevices() {
        final SharedPreferences sharedPrefs = mgrInstance.getApplicationContext().getSharedPreferences(Constants.PREFS_NAME, MODE_PRIVATE);
        final String json = sharedPrefs.getString(Constants.PREFS_PAIRED_DEVICES, null);
        if (json == null || json.isEmpty()) {
            mBtMacPairedDeviceMap = new HashMap<>();
            mBroadcastIdPairedDeviceMap = new HashMap<>();
            return;
        }
        if (mBtMacPairedDeviceMap != null) {
            mBtMacPairedDeviceMap.clear();
        }
        if (mBroadcastIdPairedDeviceMap != null) {
            mBroadcastIdPairedDeviceMap.clear();
        } else {
            mBroadcastIdPairedDeviceMap = new HashMap<>();
        }
        final Gson gson = new Gson();
        final Type type = new TypeToken<HashMap<String, LsDeviceInfo>>() { }.getType();
        mBtMacPairedDeviceMap = gson.fromJson(json, type);
        for (LsDeviceInfo lsDeviceInfo : mBtMacPairedDeviceMap.values()) {
            mBroadcastIdPairedDeviceMap.put(lsDeviceInfo.getBroadcastID(), lsDeviceInfo);
            mlsBleManager.addMeasureDevice(lsDeviceInfo);
            //Log.i(TAG, "restorePairedDevices: " + lsDeviceInfo.toString());
        }
    }

    private DeviceType getDeviceTypeOfModel(final String model) {
        return deviceTypeModelMap.get(model);
    }

    private DeviceType getDeviceTypeOfString(final String deviceConstant) {
        return deviceTypeStringMap.get(deviceConstant);
    }

    private String getModelForDeviceID(final String devId) {
        String resp = "";
        for (LsDeviceInfo lsDeviceInfo : mBtMacPairedDeviceMap.values()) {
            if (lsDeviceInfo.getDeviceId().equals(devId)) {
                resp = lsDeviceInfo.getModelNumber();
                if (resp == null) {
                    switch (lsDeviceInfo.getDeviceType()) {
                        case "08":
                            resp = Constants.ZEWA_MODEL_BPM_1; // Zewa UAM-910BT
                            break;
                        case "04":
                            resp = Constants.ZEWA_MODEL_ACTRKR_1;
                            break;
                        case "01":
                            resp = Constants.ZEWA_MODEL_WS_1;
                            break;
                        default:
                            resp = "";
                    }
                } else {
                    // look for swap outs...
                    resp = resp.replace("x","i");
                }
                if (!resp.startsWith(Constants.ZEWA_MFG_NAME))
                    resp = Constants.ZEWA_MFG_NAME + " " + resp;
                break;
            }
        }
        return resp;
    }

    private List<DeviceType> getDeviceTypes() {
        if (mScanDeviceType == null) {
            mScanDeviceType = new ArrayList<>();
            mScanDeviceType.add(DeviceType.SPHYGMOMANOMETER);
            mScanDeviceType.add(DeviceType.FAT_SCALE);
            mScanDeviceType.add(DeviceType.WEIGHT_SCALE);
            mScanDeviceType.add(DeviceType.HEIGHT_RULER);
            mScanDeviceType.add(DeviceType.PEDOMETER);
            mScanDeviceType.add(DeviceType.KITCHEN_SCALE);
        }
        Log.d(TAG, "The currently scanned device type：" + mScanDeviceType.toString());
        return mScanDeviceType;
    }

    private BroadcastType getBroadcastType() {
        Log.d(TAG, "The currently scanned broadcast type：" + mBroadcastType);
        return mBroadcastType;
    }

    private void saveTotalConnectTime() {
        final SharedPreferences sharedPrefs = this.getApplicationContext().getSharedPreferences(Constants.PREFS_NAME, MODE_PRIVATE);
        sharedPrefs.edit()
                .putInt("TotalConnections", connections)
                .putLong("LastConnectTime", connectTime)
                .apply();
        Intent RTReturn = new Intent(MainActivity.RECEIVE_MSG);
        LocalBroadcastManager.getInstance(this).sendBroadcast(RTReturn);
    }

    // ls data callback
    private ReceiveDataCallback mReceiveDataCallback = new ReceiveDataCallback() {

        @Override
        public void onDeviceConnectStateChange(DeviceConnectState var1, String var2) {
            Log.e(TAG, "onDeviceConnectStateChange: " + var1.toString() + " " + var2);
            if (var1.equals(CONNECTED_SUCCESS)) {
                connections++;
                connectTime = System.currentTimeMillis();
                saveTotalConnectTime();
            }
        }

        @Override
        public void onReceiveWeightDta_A2(final WeightData_A2 wData_A2) {
            if (wData_A2 != null) {
                WTPayloadObject po = new WTPayloadObject();
                po.dt = "WEIGHT";

                po.hi.IMEI = SafeSDK.getInstance().getIMEI();
                po.hi.SN = SafeSDK.getInstance().getSerialNumber();

                po.d.rt = Utils.fmtISO8601_receiptTime();
                po.d.ba = String.valueOf(wData_A2.getBattery());
                po.d.man = Constants.ZEWA_MFG_NAME;
                po.d.mod = Constants.ZEWA_MODEL_WS_1;
                po.d.sn = wData_A2.getDeviceId();
                po.d.wt = String.valueOf(wData_A2.getLbWeightValue());
                po.d.u = "lb";
                po.d.Kg = String.valueOf(wData_A2.getWeight());
                po.d.r2 = String.valueOf(wData_A2.getResistance_2());
                po.d.mt = Utils.fmtISO8601_measurementTime(wData_A2.getDate());

                //boolean bInserted = SafeSDK.getInstance().insertData(dataMap, DataConstant.WEIGHT_DATA_TYPE, DataConstant.NON_FDA_DBTYPE);

                String x = gson.toJson(po);
                //Log.i(TAG, x);
                String endpointURL = String.format("%s%s", ZewaLSSettings.getRootURL(), "sppost.svc/ga");
                boolean bInserted = SafeSDK.getInstance().insertData(x, "Content-type:application/json;charset=UTF-8", endpointURL);
                if (bInserted) {
                    Log.d("onReceiveWeightDta_A2", "Inserted Weight");
                } else {
                    Log.e("onReceiveWeightDta_A2", "Failed to insert Weight");
                }

                setNewDataTimeout();
            }
        }

        @Override
        public void onReceiveWeightData_A3(WeightData_A3 wData) {
            if (wData != null) {
                Log.d("onReceiveWeightData_A3", "Received A3 fat scale measurement data===========");
//                hasMeasureData = true;
            }
        }

        @Override
        public void onReceiveBloodPressureData(final BloodPressureData bpData) {
            if (bpData != null) {
                String model = getModelForDeviceID(bpData.getDeviceId());

                BPMPayloadObject po = new BPMPayloadObject();
                po.dt = "BP";

                po.hi.IMEI = SafeSDK.getInstance().getIMEI();
                po.hi.SN = SafeSDK.getInstance().getSerialNumber();

                po.d.rt = Utils.fmtISO8601_receiptTime();
                po.d.ba = String.valueOf(bpData.getBattery());
                po.d.man = Constants.ZEWA_MFG_NAME;
                po.d.mod = model;
                po.d.sn = bpData.getDeviceId() + (model.equals(Constants.ZEWA_MODEL_BPM_3) ? "" : "-" +
                        (model.equals(Constants.ZEWA_MODEL_BPM_1) ? bpData.getUserId() : (bpData.getUserId() - 1)));
                po.d.mt = Utils.fmtISO8601_measurementTime(bpData.getDate());
                po.d.u = "mmHg";
                po.d.sys = String.valueOf(bpData.getSystolic());
                po.d.dia = String.valueOf(bpData.getDiastolic());
                po.d.p = String.valueOf(bpData.getPulseRate());
                po.d.map = String.valueOf(bpData.getMeanArterialPressure());

                //boolean bInserted = SafeSDK.getInstance().insertData(dataMap, DataConstant.BP_DATA_TYPE, DataConstant.NON_FDA_DBTYPE);
                //if (bInserted) {
                //    Log.d(TAG, "onReceiveBloodPressureData: Inserted " + DataConstant.BP_DATA_TYPE);
                //} else {
                //    Log.e(TAG, "onReceiveBloodPressureData: Failed to insert " + DataConstant.BP_DATA_TYPE);
                //}

                String x = gson.toJson(po);
                //Log.i(TAG, x);
                String endpointURL = String.format("%s%s", ZewaLSSettings.getRootURL(), "sppost.svc/ga");
                boolean bInserted = SafeSDK.getInstance().insertData(x, "Content-type:application/json;charset=UTF-8", endpointURL);
                if (bInserted) {
                    Log.d("onReceiveBloodPressure", "Inserted BP");
                } else {
                    Log.e("onReceiveBloodPressure", "Failed to insert BP");
                }


                setNewDataTimeout();

            }
        }

        @Override
        public void onReceiveBloodGlucoseData(final BloodGlucoseData bgData) {
            if (bgData != null) {
                //String model = "some Model...";

                //HashMap<String, String> dataMap = new HashMap<>();
                //dataMap.put(DataConstant.PARAM_GLUCOSE, String.valueOf(bgData.getConcentration()));
                //dataMap.put(DataConstant.PARAM_UNIT, bgData.getUnit());
                //dataMap.put(DataConstant.PARAM_MEASUREMENT_TIME, Utils.getISO8601(bgData.getUtc() * 1000));
                //dataMap.put(DataConstant.PARAM_RECEIPT_TIME, Utils.fmtISO8601_receiptTime());
//                dataMap.put(Constants.DEVICE_BATTERY, String.valueOf(bpData.getBattery()));
                //dataMap.put(DataConstant.PARAM_MODEL, model);
                //dataMap.put(DataConstant.PARAM_MANUFACTURER, Constants.ZEWA_MFG_NAME);
                //dataMap.put(DataConstant.PARAM_SERIALNUMBER, bgData.getDeviceId());
                //Log.i(TAG, "onReceiveBloodGlucoseData: battery " + String.valueOf(bgData.getBattery()) );
                Log.i(TAG, "onReceiveBloodGlucoseData: model " + getModelForDeviceID(bgData.getDeviceId()));
                //boolean bInserted = SafeSDK.getInstance().insertData(dataMap, DataConstant.GLUCOSE_DATA_TYPE, DataConstant.FDA_DBTYPE);
                //if (bInserted) {
                //    Log.d(TAG, "onReceiveBloodGlucoseData: Inserted " + DataConstant.GLUCOSE_DATA_TYPE);
                //} else {
                //    Log.e(TAG, "onReceiveBloodGlucoseData: Failed to insert " + DataConstant.GLUCOSE_DATA_TYPE);
                //}
                setNewDataTimeout();
            }
        }

        @Override
        public void onReceivePedometerMeasureData(Object dataObject,
                                                  PacketProfile packetType, String sourceData)
        {
            if (dataObject == null)
                return;
            int devicePower= DeviceDataUtils.getDevicePowerPercent(dataObject, packetType);

            if(dataObject instanceof PedometerSleepData){
                //String endpointURL = String.format("%s%s", ZewaLSSettings.getRootURL(), "sppost.svc/ga");
                //PedometerSleepData obj=(PedometerSleepData)dataObject;
                //SleepData sleepData=new SleepData(obj);
                //DatabaseManager.saveSleepData(mgrInstance.getApplicationContext(),sleepData);
                //String x = gson.toJson(obj);
                //boolean bInserted = SafeSDK.getInstance().insertData(x, "Content-type:application/json;charset=UTF-8", endpointURL);
                //if (bInserted) {
                //    Log.d(TAG, "onReceivePedometerMeasureData: Inserted Sleep");
                //} else {
                //    Log.e(TAG, "onReceivePedometerMeasureData: Failed to insert Sleep");
                //}
            }
            else if(dataObject instanceof RemoteControlCmd){
                pareRemoteControlCmd((RemoteControlCmd)dataObject);
                Log.i(TAG, "onReceivePedometerMeasureData: " + DeviceDataUtils.formatStringValue(dataObject.toString()));
            }
            else if(dataObject instanceof PedometerHeartRateData) {
                String endpointURL = String.format("%s%s", ZewaLSSettings.getRootURL(), "sppost.svc/ga");
                PedometerHeartRateData obj=(PedometerHeartRateData)dataObject;
                //HeartbeatData heartbeatData=new HeartbeatData(obj);
                //DatabaseManager.saveSleepData(mgrInstance.getApplicationContext(),sleepData);
                String x = gson.toJson(obj);
                boolean bInserted = SafeSDK.getInstance().insertData(x, "Content-type:application/json;charset=UTF-8", endpointURL);
                if (bInserted) {
                    Log.d(TAG, "onReceivePedometerMeasureData: Inserted HR");
                } else {
                    Log.e(TAG, "onReceivePedometerMeasureData: Failed to insert HR");
                }
            }
            else {
                ArrayList<PedometerData> lPedoData = (ArrayList<PedometerData>)dataObject;
                for (PedometerData pd: lPedoData) {
                    pd.setBatteryPercent(devicePower);
                    Log.d(TAG, "onReceivePedometerMeasureData:-getDate: " + pd.getUtc());
                    mSafeDb.insert(new PedoEntry(pd));
                }
                //Log.i(TAG, "onReceivePedometerMeasureData:-unknown data type: " + DeviceDataUtils.formatStringValue(dataObject.toString()));
            }
            setNewDataTimeout();
        }

        @Override
        public void onReceivePedometerData(final PedometerData pData) {
            if (pData != null) {
                mSafeDb.insert(new PedoEntry(pData));
                setNewDataTimeout();
            }
        }

        @Override
        public void onReceiveHeightData(final HeightData hData) {
                if (hData != null) {
//                hasMeasureData = true;
                Log.d("onReceiveHeightData", "receive height data:");
            }
        }

        @Override
        public void onReceiveUserInfo(final WeightUserInfo proUserInfo) {
            Log.d("onReceiveUserInfo", "received");
        }

        @Override
        public void onReceiveKitchenScaleData(final KitchenScaleData kiScaleData) {
            super.onReceiveKitchenScaleData(kiScaleData);
            ZewaLSExceptionHandler.recycleApp(4);
        }

        //update and save device info ,if current connected device is generic fat and kitchen scale
        @Override
        public void onReceiveDeviceInfo(final LsDeviceInfo device) {
            //Log.d(TAG, "the current connected device info is:" + device.toString());
        }
    };

    protected void pareRemoteControlCmd(RemoteControlCmd cmd){
        if(cmd == RemoteControlCmd.EmergencyAlarm){
            //mainHandler.post(new Runnable() {
            //    @Override
            //    public void run() {
            //        Toast.makeText(getActivity(),"Emergency Alarm!",Toast.LENGTH_LONG).show();
            //    }
            //});
            Log.i(TAG, "pareRemoteControlCmd: " + cmd.toString());
        }

    }

    private synchronized void drainSafeDb() {
        final int maxPayloadSize = maxActrkrPayloadSize;
        ArrayList<PedoEntry> allEntries = new ArrayList<>();
        ArrayList<PedoEntry> payloadEntries = new ArrayList<>();
        mSafeDb.getAllEntries(allEntries);
        Log.i(TAG, "drainSafeDb: allEntries.size: " + allEntries.size());
        int payloadSize = 0;
        String lastDeviceId = "";
        for (PedoEntry pedoEntry : allEntries) {
            if (lastDeviceId.isEmpty())
                lastDeviceId = pedoEntry.deviceID;
            if ((maxPayloadSize == payloadSize) || !lastDeviceId.equals(pedoEntry.deviceID)) {
                insertPedoPayload(payloadEntries, lastDeviceId);
                payloadEntries.clear();
            }
            // add to payload
            lastDeviceId = pedoEntry.deviceID;
            payloadEntries.add(pedoEntry);
            payloadSize = payloadEntries.size();
        }
        if (payloadSize > 0) {
            insertPedoPayload(payloadEntries, lastDeviceId);
        }
        SafeSDK.getInstance().drainSafe();
    }

    private synchronized void insertPedoPayload(ArrayList<PedoEntry> payloadEntries, String deviceId) {
        ArrayList<Integer> ids = new ArrayList<>();
        PedoPayload pedoPayload = new PedoPayload(deviceId);
        for (PedoEntry pe : payloadEntries) {
            ids.add(pe.ID);
            pedoPayload.add(pe);
        }

        ACTPayloadObject po = new ACTPayloadObject();
        po.dt = "ACTRKR";

        po.hi.IMEI = SafeSDK.getInstance().getIMEI();
        po.hi.SN = SafeSDK.getInstance().getSerialNumber();

        po.d.rt = gson.toJsonTree(pedoPayload.receiptTime.toArray(), long[].class).toString();
        po.d.b = gson.toJsonTree(pedoPayload.batteryLevel.toArray(), float[].class).toString();
        po.d.b2 = gson.toJsonTree(pedoPayload.batteryLevel2.toArray(), int[].class).toString();
        po.d.man = pedoPayload.manufacturer;
        po.d.mod = pedoPayload.modelName;
        po.d.sn = pedoPayload.deviceID;
        po.d.mt = gson.toJsonTree(pedoPayload.measurementTime.toArray(), long[].class).toString();

        po.d.ws  = gson.toJsonTree(pedoPayload.walkSteps.toArray(), int[].class).toString();
        po.d.rs = gson.toJsonTree(pedoPayload.runSteps.toArray(), int[].class).toString();
        po.d.di = gson.toJsonTree(pedoPayload.distance.toArray(), int[].class).toString();
        po.d.et = gson.toJsonTree(pedoPayload.exerciseTime.toArray(), int[].class).toString();
        po.d.ca = gson.toJsonTree(pedoPayload.calories.toArray(), float[].class).toString();
        po.d.il = gson.toJsonTree(pedoPayload.intensityLevel.toArray(), int[].class).toString();
        po.d.sl = gson.toJsonTree(pedoPayload.sleepStatus.toArray(), int[].class).toString();
        po.d.ea = gson.toJsonTree(pedoPayload.exerciseAmount.toArray(), float[].class).toString();
        po.d.tzo = gson.toJsonTree(pedoPayload.measureTimeUtcOffset.toArray(), int[].class).toString();

        String x = gson.toJson(po);
        //Log.i(TAG, x);
        String endpointURL = String.format("%s%s", ZewaLSSettings.getRootURL(), "sppost.svc/ga");
        boolean bInserted = SafeSDK.getInstance().insertData(x, "Content-type:application/json;charset=UTF-8", endpointURL);
        if (bInserted) {
            mSafeDb.deleteByIDS(ids); // clear the local safe - the global safe will take care of it...
            Log.d("insertPedoPayload", "Inserted");
        } else {
            Log.e("insertPedoPayload", "Failed to insert");
        }


        // insert into intel SDK, then delete from SafeDb...
        //boolean bInserted = SafeSDK.getInstance().insertData(dataMap, Constants.ACTRKR_DATA_TYPE, DataConstant.NON_FDA_DBTYPE);
        //if (bInserted) {
        //    Log.d(TAG, "insertPedoPayload: Inserted " + mSafeDb.deleteByIDS(ids) + " " + Constants.ACTRKR_DATA_TYPE + " item(s).");
        //} else {
        //    Log.e(TAG, "insertPedoPayload: Failed to insert " + payloadEntries.size() + " " + Constants.ACTRKR_DATA_TYPE + " item(s).");
        //}
        
    }

    private void setLsBleStateIdle() {
        switch (mLsState) {
            case IDLE:
                // ignore
                break;

            case SEARCH_ZEWA:
                Log.d(TAG, "Stopping Zewa Searching");
//                isScanning = false;
                if (mlsBleManager != null) {
                    mlsBleManager.stopSearch();
                }
                break;

            case SEARCH_ZEWA_DONE:
                // shouldn't happen
                // ignore
                break;

            case PAIR_ZEWA:
                Log.d(TAG, "Stopping Zewa Pairing");
                if (mlsBleManager != null) {
                    mlsBleManager.cancelDevicePairing(lsDevicePairing);
                }
                break;

            case DATA_ZEWA:
                Log.d(TAG, "Stopping Zewa receiveData");
                if (mlsBleManager != null) {
                    mlsBleManager.stopDataReceiveService();
                }
                break;

            default:
                Log.e(TAG, "Unhandled ZewaManager BleState=" + mLsState.toString());
                break;
        }
        if( mLsState != BleState.IDLE) {
            SystemClock.sleep(Constants.STATE_TRANSITION_TIME);
        }
        mLsState = BleState.IDLE;
    }

    private boolean isTimeoutSet = false;
    private synchronized void setNewDataTimeout() {
        if (isTimeoutSet)
            mgrHandler.removeCallbacks(newDataTimeout);
        isTimeoutSet = true;
        mgrHandler.postDelayed(newDataTimeout, 1250);
    }

    private Runnable newDataTimeout = new Runnable() {
        @Override
        public void run() {
            isTimeoutSet = false;
            drainSafeDb();
        }
    };

}

