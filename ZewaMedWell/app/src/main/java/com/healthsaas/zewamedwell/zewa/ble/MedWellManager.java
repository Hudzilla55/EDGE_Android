package com.healthsaas.zewamedwell.zewa.ble;

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

import com.healthsaas.zewamedwell.Constants;
import com.healthsaas.zewamedwell.Utils;
import com.healthsaas.zewamedwell.MedWellReminderGetTask;
import com.healthsaas.zewamedwell.ZewaMedWellManager;
import com.healthsaas.zewamedwell.zewa.dataModels.deviceObject;
import com.healthsaas.zewamedwell.zewa.dataModels.medWellReminder;
import com.healthsaas.zewamedwell.zewa.dataModels.medWellReminders;
import com.healthsaas.zewamedwell.zewa.dataModels.payloadObject;
import com.healthsaas.zewamedwell.zewa.medwell.MedWellSettings;
import com.healthsaas.zewamedwell.zewa.medwell.MedWellTransferState;
import com.healthsaas.zewamedwell.zewa.medwell.SDKConstants;
import com.healthsaas.zewamedwell.zewa.medwell.rawEvents;
import com.healthsaas.zewamedwell.zewadb.BtDeviceDb;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonParseException;
import com.healthsaas.edge.safe.SafeSDK;


import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.GregorianCalendar;
import java.util.Locale;
import java.util.Timer;
import java.util.TimerTask;

import static com.healthsaas.zewamedwell.Utils.getISO8601;


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

public class MedWellManager extends MedWellManagerService {
    private static final String TAG = MedWellManager.class.getSimpleName();


    private static final long MAX_IDLE_TIME = 60 * 1000; // wait for up to 60 seconds...

    private static final int MEDWELL_REMINDER_BLOCK_START = 0xA800;
    private int MEDWELL_EVENT_COUNT = 4000;
    Context mContext;
    MedWellTransferState mTransferState;
    rawEvents mTransferData;
    private Gson Json = new Gson();
    private int logIndex;
    private int logIndexOnConnect;
    private boolean endOfLog;
    private long mwClock;

    private Handler mHandler = new Handler();
    private boolean mRetryConnect;
    private int mMaxConnectRetries = 3;
    private static final Object sOnStartLock = new Object();
    private Timer mStopTimer = new Timer();

    private int transferCycles;
    private int transferStartIndex;
    private int mConnectRetries = 0;
    private int maxPayloadSize = 48;
    private boolean MedWellReset = false;

    boolean mServiceStarted = false;
    int mDeviceSyncIndex = 0;
    boolean mSyncCycleRunning = false;
    ArrayList<deviceObject> mDevices;
    BtDeviceDb mBtDb;

    public MedWellManager() {
        setModelUUIDS("MedWell.V0");
    }

    private void setModelUUIDS(String model) {
        if (model.equals("MedWell.V0")) {
            BLE_SERVICE_UUID = SDKConstants.MEDWELL_SERVICE_UUID;
            BLE_CLOCK_UUID = SDKConstants.MEDWELL_CLOCK_UUID;
            BLE_TEST_PROGRAM_UUID = SDKConstants.MEDWELL_TEST_PROGRAM_UUID;
            BLE_READ_BLOCK_INDEX_UUID = SDKConstants.MEDWELL_READ_BLOCK_INDEX_UUID;
            BLE_BLOCK_UUID = SDKConstants.MEDWELL_BLOCK_UUID;
            BLE_STATUS_UUID = SDKConstants.MEDWELL_STATUS_UUID;
            BLE_WRITE_UUID = SDKConstants.MEDWELL_WRITE_UUID;
            baseSecurityKey[0] = 0x6E;
            baseSecurityKey[1] = 0x72;
            baseSecurityKey[2] = 0x46;
            baseSecurityKey[3] = 0x35;
            baseSecurityKey[4] = 0x31;
            baseSecurityKey[5] = 0x38;
            baseSecurityKey[6] = 0x32;
            baseSecurityKey[7] = 0x32;
        }
    }

    //
    // service stuff
    //
    private final IBinder mBinder = new LocalBinder();

    public class LocalBinder extends Binder {
        public MedWellManager getService() {
            // Return this instance of LocalService so clients can call public methods
            return MedWellManager.this;
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
        mDevices = new ArrayList<>();

        maxPayloadSize = MedWellSettings.getMaxPayloadSize();

        mBtDb = ZewaMedWellManager.getMgrInstance().getBtDb();
        mBtDb.getAllDevices(mDevices);
        if (super.initialize(mMedWellManagerServiceCallback))
            Log.i(TAG, "onCreate: super initialized");
        Log.i(TAG, "Service Created.");

    }

    @Override
    public void onDestroy() {
        close();
        Log.i(TAG, "Service Destroyed.");
        super.onDestroy();
    }

    @Override
    public void connectionCompleteInitialization() {
        // the clock was just written, creating a new sync event, account for this in case the sync fails.
        deviceObject devO = mDevices.get(mDeviceSyncIndex);
        devO.outstandingSyncCount = devO.outstandingSyncCount + 1;
        mDevices.set(mDeviceSyncIndex, devO);
        mBtDb.update(devO);
        Log.i(TAG, "New Outstanding Sync Count: " + devO.outstandingSyncCount);

        logIndex = convertByteArrayToInt(statusBlockData, 4);
        if (logIndex == 0) {
            logIndex = MEDWELL_EVENT_COUNT;
        }

        logIndex--;
        logIndexOnConnect = logIndex;
        Log.i(TAG, "Log Index on Connect: " + logIndexOnConnect);
    }

    @Override
    protected void updateReadBlocksState(BLEReadBlocksState newState) {
        BLEReadBlocksState lastState = mReadBlocksState;

        // update state
        super.updateReadBlocksState(newState);

        // manage transfer state
        if (lastState == BLEReadBlocksState.BLE_READ_BLOCKS_ENABLE && mReadBlocksState == BLEReadBlocksState.BLE_READ_BLOCKS_OK) {
            if (mTransferState == MedWellTransferState.MEDWELL_TRANSFER_READ || mTransferState == MedWellTransferState.MEDWELL_TRANSFER_RETRY) {
                if (transferCycles <= 0 || endOfLog) {
                    didCompleteMedWellTransfer(transferCycles);
                    finishDeviceCycle();
                } else {
                    mTransferState = MedWellTransferState.MEDWELL_TRANSFER_READ;
                    readBlock(logIndex);
                }
            }
        } else if (lastState == BLEReadBlocksState.BLE_READ_BLOCKS_ENABLE && mReadBlocksState == BLEReadBlocksState.BLE_READ_BLOCKS_TIMEOUT) {
            if (mTransferState == MedWellTransferState.MEDWELL_TRANSFER_READ) {
                mTransferState = MedWellTransferState.MEDWELL_TRANSFER_RETRY;
                readBlock(logIndex);
            } else if (mTransferState == MedWellTransferState.MEDWELL_TRANSFER_RETRY) {
                // failed read operation
                didCompleteMedWellTransfer(transferCycles);
                finishDeviceCycle();
            }
        }
    }

    @Override
    protected void processBlock(byte[] currData) {
        long t;
        int d;
        int index = convertByteArrayToInt(currData, 0);
        logIndex = index & 0xFFFE;
        resetStopTimer();

        if (index != transferStartIndex || ((index & 0x01) == 0x01)) {
            // check upper record of data
            t = convertByteArrayToHalfLong(currData, 12);
            d = convertByteArrayToInt(currData, 16);

            if (t == 0xFFFFFFFFL) {
                // end of log
                endOfLog = true;
            } else if (d == 0x9000) {
                // found another sync marker
                transferCycles--;
            }

            if (d == 0xA000) {
                // found a reset - set reset flag for reminder processing
                MedWellReset = true;
            }

            if (transferCycles != 0 && !endOfLog) {
                mTransferData.createEventAndInsert(logIndex + 1, t, d);
                Log.i(TAG, String.format(Locale.US, "processBlock upper record - index: %d, timeStamp: %d (%s), data: %d", logIndex + 1, t, getISO8601(t * 1000), d));
            }
        }


        if (transferCycles != 0 && !endOfLog) {
            // now check lower record

            t = convertByteArrayToHalfLong(currData, 4);
            d = convertByteArrayToInt(currData, 8);

            if (t == 0xFFFFFFFFL) {
                // end of log
                endOfLog = true;
            } else if (d == 0x9000) {
                // found another sync marker
                transferCycles--;
            }

            if (d == 0xA000) {
                // found a reset - set reset flag for reminder processing
                MedWellReset = true;
            }

            if (transferCycles != 0 && !endOfLog) {
                mTransferData.createEventAndInsert(logIndex, t, d);
                Log.i(TAG, String.format(Locale.US, "processBlock lower record - index: %d, timeStamp: %d (%s), data: %d", logIndex, t, getISO8601(t * 1000), d));
            }

        }
        if (logIndex == 0) {
            logIndex = MEDWELL_EVENT_COUNT - 2;
        } else {
            logIndex -= 2;
        }

        if (transferCycles <= 0 || endOfLog) {
            /* read complete */
            updateReadBlocksState(BLEReadBlocksState.BLE_READ_BLOCKS_OK);
        } else {
            /* not done, reset the timer */
            updateReadBlocksState(BLEReadBlocksState.BLE_READ_BLOCKS_ENABLE);
            readBlock(logIndex);
        }
    }

    public void resetLogIndex() {
        logIndex = logIndexOnConnect;
    }

    public void transferData(int cycles) {
        if (cycles > 0) {

            Log.i(TAG, "transferData: Starting Data Transfer...");
            // setup transfer info
            transferCycles = cycles;

            endOfLog = false;
            MedWellReset = false;
            mTransferData.clearEvents();
            mTransferState = MedWellTransferState.MEDWELL_TRANSFER_READ;
            transferStartIndex = logIndex;
            logReadBlocks = false;
            readBlock(transferStartIndex);
            resetStopTimer();
        }
    }

    public void setDeviceInitKey() {
        deviceObject dev = mDevices.get(mDeviceSyncIndex);
        String devModel = dev.modelName;
        //String passKey = dev.passKey;
        //String deviceSN = dev.serialNumber;
        setModelUUIDS(devModel);
    }

    public void writeReminder(medWellReminder r) {
        if (r.well < 4) {
            byte[] configData = new byte[16];
            long startTime;

            for (int i = 0; i < 16; i++) {
                configData[i] = 0x00;
            }

            if (r.blinkSpeed < 2)
                r.blinkSpeed = 2;

            if (r.repeatInterval > 0) {
                GregorianCalendar gc = new GregorianCalendar();
                int year = gc.get(Calendar.YEAR), month = gc.get(Calendar.MONTH), day = gc.get(Calendar.DAY_OF_MONTH);

                gc.set(year, month, day, r.startTimeHours, r.startTimeMinutes, 0);
                startTime = gc.getTimeInMillis() / 1000;

                while (startTime <= (System.currentTimeMillis() / 1000) + 5) // give a little margin to ensure the reminder is set before time passes...
                    startTime += r.repeatInterval;

            } else { // clear/empty reminder for well
                startTime = 0;
                r.repeatInterval = 0;
                r.blinkSpeed = 0;
                r.advanceAfter = 0;
                r.beepCount = 0;
                r.beepInterval = 0;
            }

            Log.d(TAG, "writeReminder.startTime: " + startTime);
            convertIntToByteArray((int) (0xFFFFFFFFL & startTime), configData, 0);
            convertIntToByteArray((int) (0xFFFFFFFFL & r.repeatInterval), configData, 4);

            configData[8] = (byte) (r.advanceAfter & 0xFF);
            configData[9] = (byte) ((r.advanceAfter >> 8) & 0xFF);
            configData[10] = (byte) (r.blinkSpeed & 0xFF);
            configData[11] = (byte) (r.beepInterval & 0xFF);
            configData[12] = (byte) (r.beepCount & 0xFF);

            writeBlock(MEDWELL_REMINDER_BLOCK_START + r.well, configData);
        }
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
            setDeviceInitKey();
            resetStopTimer();
//            if (!connect(dev.macAddress)) {
//                finishDeviceCycle();
//            }
            connect(dev.macAddress);
        }
    }

    private void finishDeviceCycle() {
        resetStopTimer();
        if (!mRetryConnect && queueSize() > 0) { // writing reminders? give them time to process out...
            mHandler.postDelayed(delayFinishDeviceCycle, 750);
            return;
        }
        mHandler.postDelayed(doFinishDeviceCycle, 750);
    }

    private Runnable delayFinishDeviceCycle = new Runnable() {
        @Override
        public void run() {
            finishDeviceCycle();

        }
    };

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

        Log.i(TAG, "MedWell Data Cycle Complete - Status: " + status);

        ZewaMedWellManager.sendMedWellSyncDone();
    }

    private void didCompleteMedWellTransfer(int remainingCycles) {
        resetStopTimer();
        Log.i(TAG, "did complete MedWell transfer remainingCycles=" + remainingCycles);
        mTransferState = MedWellTransferState.MEDWELL_TRANSFER_IDLE;
        boolean inSafe = insertEventsInSafe();
        deviceObject devO = setMedWellReminders();
        if (inSafe) { // if data not in safe, then it will need to be re-harvested, don't update outstanding count...
            devO.outstandingSyncCount = 1 + (remainingCycles > 0 ? remainingCycles : 0);
        } else {
            Log.e(TAG, "Failed to insert events in SAFE.");
        }
        devO.batteryLevel = batteryLevel;
        devO.lastStatus = "Success!";
        devO.lastContact = getISO8601(System.currentTimeMillis());
        if (!devO.paired) { // first contact with this device - update portal, and save new backup.
            devO.paired = true;
        }
        mDevices.set(mDeviceSyncIndex, devO);
        mBtDb.update(devO);
    }

    private MedWellManagerServiceCallback mMedWellManagerServiceCallback = new MedWellManagerServiceCallback() {

        ////////////////////////////////////////////////////////////////////////////////
        // Step 7: Implement the callback for a completed connection
        //
        //  The BLEDeviceManagerServiceCallback interface function
        //
        //     public void didCompleteConnect(boolean success)
        //
        //  is called when the connection process is complete. The pass/fail status of
        //  that process is provided by the parameter, success. If success is true, the
        //  application should initiate a transfer of the MedWell data. If false, the
        //  application should handle the exception and re-try the connection.
        //
        //  If the connection is established, the transfer of data can be initiated using
        //  the MedWellManagerService method
        //
        //     public void transferData(MedWellData data,
        //                             int cycles,
        //                             int rate,
        //                             boolean sync)
        //
        //  The parameter, data, is a reference to a MedWellData object to store the data,
        //  cycles specifies the number of sync cycles to download from the device, rate
        //  is the transfer rate used during the download, and sync specifies whether a
        //  clock sync should occur during the transfer.
        //
        //  The rate parameter should be determined using the MedWellManagerService
        //  inherited method
        //
        //     public int getTargetRate()
        //
        //  which uses the current communication configuration to calculate the optimum
        //  transfer rate.
        //
        ////////////////////////////////////////////////////////////////////////////////

        public void didCompleteConnect(boolean success) {

            if (success && firmWareCheck()) {

                Log.i(TAG, "Connected...");
                resetStopTimer();

                mRetryConnect = false;
                mConnectRetries = 0;
                didUpdateReadProgress(0.75d);

                mHandler.postDelayed(delayTransferData, 950);
            } else {
                deviceObject devO = mDevices.get(mDeviceSyncIndex);
                disconnect();
                resetStopTimer();
                if (failedAuthentication) {
                    devO.passKey = "";
                    devO.lastStatus = "Authentication Failed";
//                    if (!devO.paired) {
//                        mWebServices.deviceUpdate(devO, manufacturer, hardwareRevision, firmwareRevision, softwareRevision);
//                    }
                    mRetryConnect = false;
                    mConnectRetries = 0;
                } else if (!firmWareCheck()) {
                    Log.i(TAG, String.format("OLD FIRMWARE - %s", firmwareRevision));
                    devO.lastStatus = "OLD FIRMWARE";
                    devO.macAddress = "";
//                    if (!devO.paired) {
//                        mWebServices.deviceUpdate(devO, manufacturer, hardwareRevision, firmwareRevision, softwareRevision);
//                    }
                    mRetryConnect = false;
                    mConnectRetries = 0;
                } else {
                    Log.i(TAG, String.format("Connection Failed - %b", mRetryConnect));
                    devO.lastStatus = "Connection Failed";
                    didUpdateReadProgress(0.125d);

                    if (android.os.Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                        mMaxConnectRetries = 6;
                    }
                    mRetryConnect = (mConnectRetries++ < mMaxConnectRetries);
                    SystemClock.sleep(5000);
                }
                devO.lastStatus = getISO8601(System.currentTimeMillis());
                mBtDb.update(devO);
                mDevices.set(mDeviceSyncIndex, devO);
                finishDeviceCycle();
            }
        }

        public void didConnect() {
            resetStopTimer();
            didUpdateReadProgress(0.5d);
        }

        public void didStartConnect() {
            resetStopTimer();
            didUpdateReadProgress(0.25d);
        }

        ////////////////////////////////////////////////////////////////////////////////
        // Step 9: Implement callback for complete write reminder (optional)
        //
        //  The MedWellManagerServiceCallback interface function
        //
        //     public void didWriteBlock(boolean success)
        //
        //  is called after every the completion of a block write. The application can
        //  now disconnect from the device using a call to the MedWellManagerService
        // inherited method
        //
        //     public void disconnect()
        //
        //  which terminates the current connection.
        //
        ////////////////////////////////////////////////////////////////////////////////

        public void didWriteBlock(boolean success) {
            Log.i(TAG, "write callback");
            //disconnect();
            resetStopTimer();
        }

        public void didDisconnect() {
            //Log.index(TAG, "disconnected...");
            resetStopTimer();
            didUpdateReadProgress(0.0d);
        }

        public void didReadClock(long x) {
            mwClock = x;
            Log.i(TAG, "readClock callback - " + String.valueOf(mwClock));
        }

         public void didCompleteRead(boolean success) {
            Log.i(TAG, "complete read callback - success:" + success);
            logReadBlocks = true;
            resetStopTimer();
        }

        public void didUpdateReadProgress(double progress) {
            Log.d(TAG, String.format("MedWell SYNC_STATUS=PROGRESS, PROGRESS=%.2f", progress));
        }
    };

    private boolean firmWareCheck() {
      boolean resp = false;
      if (firmwareRevision.isEmpty() || firmwareRevision.startsWith("180531"))
          resp = true;
      return resp;
    }

    private Runnable delayTransferData = new Runnable() {
        @Override
        public void run() {
            _delayTransferData();
        }
    };

    private void _delayTransferData() {
        resetLogIndex();
        int syncCount = mDevices.get(mDeviceSyncIndex).outstandingSyncCount; // the default is to stop downloading data after the 2nd sync marker has been encountered (this cycles marker + previous marker).
        Log.i(TAG, "delayTransferData.run: syncCount==" + syncCount); // this is to catch failed download cycles - when a download is successful outstandingSyncCount is reset to 1 to account for previous marker.
        transferData(syncCount); // if previous download(s) have failed, syncCount will be greater than 2 - 2 is the default value (all downloads have been successful - previous sync marker + this sync marker).
    }

    private deviceObject setMedWellReminders() {
        deviceObject dev = mBtDb.getDeviceBySN(mDevices.get(mDeviceSyncIndex).serialNumber); // get fresh device object data
        // need to detect time zone offset changes (daylight/standard + travel zone changes.)...
        if (MedWellReset || !dev.timeZoneOffset.equals(getTimeZoneOffset())) { // only update if schedule changed, time zone offset change, or device reset...
            // the schedule data comes from the portal, and is stored in each device objects extraInfo property.
            // when updated schedule comes from portal, the timeZoneOffset is cleared so we will land here - the portal is the schedule master.
            String extraInfo = dev.extraInfoJson; // get reminders for this device
            if ((MedWellReset && dev.sortUpdatedTime < (System.currentTimeMillis() - 86400000)) || extraInfo.isEmpty()) {
                // unconditionally pull reminders on missing (empty) schedule, and for resets with 24h old+ settings.
                extraInfo = getMedWellReminders(dev);
                dev.timeZoneOffset = "";
            }
            medWellReminders reminders = null;
            if (extraInfo != null && !extraInfo.isEmpty()) {
                try {
                    reminders = Json.fromJson(extraInfo, medWellReminders.class);
                } catch (JsonParseException e) {
                    Log.e(TAG, "extraInfo JSON err: " + e.toString());
                    reminders = null;
                }
            }
            if ((reminders != null) && (reminders.size() > 0)) {
                for (int x = 0; x < reminders.size() && x < 4; x++) {
                    medWellReminder r = reminders.get(x);
                    writeReminder(r);
                }
                dev.sortVer = reminders.get(0).sortVer;
                dev.sortSyncedTime = System.currentTimeMillis();
                dev.timeZoneOffset = getTimeZoneOffset(); // update with current time zone offset to keep from setting every sync cycle.

                writeTestProgram(SDKConstants.MEDWELL_TEST_PROGRAM_BUZZ); // give indication the reminders have been programmed.
                writeTestProgram(SDKConstants.MEDWELL_TEST_PROGRAM_LED); // give indication the reminders have been programmed.
                writeTestProgram(SDKConstants.MEDWELL_TEST_PROGRAM_BUZZ); // give indication the reminders have been programmed.
            } else {
                dev.timeZoneOffset = "";
                if (dev.sortVer < 1 && MedWellReset) {
                    // new device, zero out the reminders to shut device up.
                    for (int x = 0; x < 4; x++) {
                        writeReminder(new medWellReminder(x, 0));
                    }
                }
            }
            mBtDb.update(dev);
            mDevices.set(mDeviceSyncIndex, dev);
            MedWellReset = false;
        }
        return dev;
    }

    public static String getTimeZoneOffset() {
        String resp;
        SimpleDateFormat sdf = new SimpleDateFormat("Z", Locale.US);
        resp = sdf.format(new Date(System.currentTimeMillis()));
        return resp;
    }

    public String getMedWellReminders(deviceObject dev) {
        if (dev.sortVer < 1)
            return "";
        MedWellReminderGetTask mwrGet = new MedWellReminderGetTask();
        if (mwrGet.getMedWellReminder(dev)) {
            dev.timeZoneOffset = "";
        }
        return dev.extraInfoJson;
    }

    private class EventEntry {
        private final int INDEX = 0;
        private final int TIMESTAMP = 1;
        private final int DATA = 2;
        private final int ELEMENTS = 3;
        int index;
        long timeStamp;
        int data;
        // Backward compatible with existing code
        EventEntry(final int index, final long timeStamp, final int data) {
            this(new long[] {(long)index, timeStamp, (long)data });
        }
        EventEntry(final long[] entry) {
            if (entry.length != ELEMENTS)
                return;
            this.index = (int)entry[INDEX];
            this.timeStamp = entry[TIMESTAMP];
            this.data = (int)entry[DATA];
        }
        long[] toArray() {
            long [] resp = new long[ELEMENTS];
            resp[INDEX] = (long)this.index;
            resp[TIMESTAMP] = this.timeStamp;
            resp[DATA] = (long)this.data;
            return  resp;
        }
    }

    private boolean insertEventsInSafe() {
        final int MAX_ENTRIES = maxPayloadSize;
        int dbRows = 0;

        int eventCount = mTransferData.getEventCount();
        if (eventCount == 0) {
            return false;
        }

        deviceObject dev = mDevices.get(mDeviceSyncIndex);

        payloadObject po = new payloadObject();
        po.dt = "PILLBOX";

        po.hi.IMEI = SafeSDK.getInstance().getIMEI();
        po.hi.SN = SafeSDK.getInstance().getSerialNumber();

        po.d.mwt = String.valueOf(mwClock);
        po.d.rt = Utils.fmtISO8601_receiptTime();
        po.d.ba = String.valueOf(batteryLevel);
        po.d.man = Constants.ZEWA_MFG_NAME;
        po.d.mod = Constants.ZEWA_MODEL_MEDWELL;
        po.d.sn = dev.serialNumber;

        Gson gson = new GsonBuilder().disableHtmlEscaping().create();
        JsonArray jArrEvents = new JsonArray();
        int nStored = 0;
        for (int i = 0; i < eventCount; i++) {
            EventEntry eventEntry = new EventEntry(
                    mTransferData.getEventIndex(i),
                    mTransferData.getEventTimeStamp(i),
                    mTransferData.getEventData(i));
            jArrEvents.add(gson.toJsonTree(eventEntry.toArray(), long[].class));
            if (++nStored >= MAX_ENTRIES) {
                po.d.ev = gson.toJson(jArrEvents);
                String x = gson.toJson(po);
                //Log.i(TAG, x);
                dbRows +=  insertEventsSafeDb(x) ? 1 : 0;
                jArrEvents = new JsonArray();
                nStored = 0;
            }
        }
        if (nStored > 0) {
            po.d.ev = gson.toJson(jArrEvents);
            String x = gson.toJson(po);
            //Log.i(TAG, x);
            dbRows +=  insertEventsSafeDb(x) ? 1 : 0;

        }
        return (dbRows > 0);
    }

    private boolean insertEventsSafeDb(String Payload) {
        String endpointURL = String.format("%s%s", MedWellSettings.getRootURL(), "sppost.svc/ga");
        return SafeSDK.getInstance().insertData(Payload, "Content-type:application/json;charset=UTF-8", endpointURL);
    }

/*
    private void insertEventsIntelDB(JsonObject data_jObj) {
        boolean bInserted = DataSDK.getInstance().insertData(
                data_jObj.toString(),
                Constants.MEDWELL_DATA_TYPE,
                DataConstant.NON_FDA_DBTYPE);
        if (bInserted) {
            Log.d(TAG, "Inserted " + Constants.MEDWELL_DATA_TYPE);
        } else {
            Log.e(TAG, "Failed to insert " + Constants.MEDWELL_DATA_TYPE);
        }
    }
*/
}

