package com.healthsaas.zewamedwell.zewa.ble;

import android.annotation.TargetApi;
import android.app.Service;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCallback;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattDescriptor;
import android.bluetooth.BluetoothGattService;
import android.bluetooth.BluetoothManager;
import android.bluetooth.BluetoothProfile;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Binder;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.IBinder;
import android.os.Looper;
import android.os.Message;
import android.os.Process;
import android.os.SystemClock;
import android.util.Log;

import com.healthsaas.zewamedwell.Constants;
import com.healthsaas.zewamedwell.MainActivity;
import com.healthsaas.zewamedwell.ZewaMedWellApp;
import com.healthsaas.zewamedwell.zewa.medwell.SDKConstants;

import java.lang.ref.WeakReference;
import java.util.LinkedList;
import java.util.Objects;
import java.util.Queue;
import java.util.Timer;
import java.util.TimerTask;
import java.util.UUID;

import javax.crypto.Cipher;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;

import androidx.localbroadcastmanager.content.LocalBroadcastManager;

@TargetApi(23)
public abstract class MedWellManagerService extends Service {
    private final static String TAG = MedWellManagerService.class.getSimpleName();

    protected String BLE_SERVICE_UUID;
    protected String BLE_CLOCK_UUID;
    protected String BLE_TEST_PROGRAM_UUID;
    protected String BLE_READ_BLOCK_INDEX_UUID;
    protected String BLE_BLOCK_UUID;
    protected String BLE_STATUS_UUID;
    protected String BLE_WRITE_UUID;

    protected String DEVICE_INFO_SERVICE_UUID = "0000180a-0000-1000-8000-00805f9b34fb";
    protected String DEVICE_INFO_SYSTEM_ID_UUID = "00002a23-0000-1000-8000-00805f9b34fb";
    protected String DEVICE_INFO_MODEL_NUM_UUID = "00002a24-0000-1000-8000-00805f9b34fb";
    protected String DEVICE_INFO_SERIAL_NUM_UUID = "00002a25-0000-1000-8000-00805f9b34fb";
    protected String DEVICE_INFO_FW_REV_UUID = "00002a26-0000-1000-8000-00805f9b34fb";
    protected String DEVICE_INFO_HW_REV_UUID = "00002a27-0000-1000-8000-00805f9b34fb";
    protected String DEVICE_INFO_SW_REV_UUID = "00002a28-0000-1000-8000-00805f9b34fb";
    protected String DEVICE_INFO_MANF_NAME_UUID = "00002a29-0000-1000-8000-00805f9b34fb";

    protected String BATTERY_SERVICE_UUID = "0000180f-0000-1000-8000-00805f9b34fb";
    protected String BATTERY_LEVEL_UUID = "00002a19-0000-1000-8000-00805f9b34fb";

    protected String NOTIFY_DESCRIPTOR_UUID = "00002902-0000-1000-8000-00805f9b34fb";

    public MedWellManagerService() {
        BLE_SERVICE_UUID = SDKConstants.MEDWELL_SERVICE_UUID;
        BLE_CLOCK_UUID = SDKConstants.MEDWELL_CLOCK_UUID;
        BLE_TEST_PROGRAM_UUID = SDKConstants.MEDWELL_TEST_PROGRAM_UUID;
        BLE_READ_BLOCK_INDEX_UUID = SDKConstants.MEDWELL_READ_BLOCK_INDEX_UUID;
        BLE_BLOCK_UUID = SDKConstants.MEDWELL_BLOCK_UUID;
        BLE_STATUS_UUID = SDKConstants.MEDWELL_STATUS_UUID;
        BLE_WRITE_UUID = SDKConstants.MEDWELL_WRITE_UUID;
    }

    private MedWellManagerServiceCallback mCallback;
    private BluetoothManager mBluetoothManager;
    private BluetoothAdapter mBluetoothAdapter;
    private BluetoothGatt mBluetoothGatt;

    private BluetoothGattCharacteristic mClockChar;
    private BluetoothGattCharacteristic mTestProgramChar;
    private BluetoothGattCharacteristic mReadBlockIndexChar;
    private BluetoothGattCharacteristic mBlockChar;
    private BluetoothGattCharacteristic mStatusChar;
    private BluetoothGattCharacteristic mWriteChar;
    private BluetoothGattCharacteristic mBatteryLevelChar;
    private BluetoothGattCharacteristic mSystemIdNumberChar;
    private BluetoothGattCharacteristic mModelNumberChar;
    private BluetoothGattCharacteristic mSerialNumberChar;
    private BluetoothGattCharacteristic mFirmwareRevisionChar;
    private BluetoothGattCharacteristic mHardwareRevisionChar;
    private BluetoothGattCharacteristic mSoftwareRevisionChar;
    private BluetoothGattCharacteristic mManufacturerChar;

    public com.healthsaas.zewamedwell.zewa.ble.BLEDeviceBlocks deviceBlocks = new BLEDeviceBlocks();

    private Queue<BluetoothGattCharacteristic> readQueue = new LinkedList<>();
    private Queue<WriteCharacteristicData> writeQueue = new LinkedList<>();

    private class WriteCharacteristicData {
        BluetoothGattCharacteristic characteristic;
        byte[] data;
    }

    public String systemIdNumber;
    public String hardwareAddress;
    public String modelNumber;
    public String serialNumber;
    public String firmwareRevision = "";
    public String hardwareRevision;
    public String softwareRevision;
    public String manufacturer;

    private BLEManagerState mConnectState = BLEManagerState.BLE_STANDBY;
    protected BLEReadBlocksState mReadBlocksState = BLEReadBlocksState.BLE_READ_BLOCKS_IDLE;

    long connectTime = 0;
    int connections = 0;
    long totalConnectTime = 0;
    long lastConnectTime = 0;
    int ble133ErrorCount = 0;
    double readProgress;

    public int batteryLevel;
    public int connectionInterval;
    public long deviceClock;
    public long deviceClockDriftOnConnect;
    private int initReadFlag;
    public byte[] statusBlockData = new byte[20]; // char 05
    public byte[] writeBlockData = new byte[20]; // char 06
    public byte[] readBlockData = new byte[20]; // char 04
    public byte[] systemIdBytes = new byte[8];
    public byte[] baseSecurityKey = new byte[8];

    public boolean enableFastConnect = false;
    public boolean enableSecurity = true;
    public boolean connectionSecure;
    public boolean failedAuthentication = false;
    public boolean storeEncryptedData;
    public boolean logReadBlocks = true;

    byte[] securityInitKey = new byte[16];
    byte[] securitySessionKey = new byte[16];

    private void initBlocks() {
//    	byte[] blankData = new byte[20];
//    	for(int i=0; i<20; i++)
//    		blankData[i] = (byte) 0x00;
        Log.i(TAG, "Starting block init");
        deviceBlocks.clearBlocks();
        Log.i(TAG, "Block init complete");
    }

    private enum BLEDiscoverState {
        BLE_DISCOVER_IDLE,
        BLE_DISCOVER_PENDING,
        BLE_DISCOVER_DONE
    }

    private BLEDiscoverState discoverDeviceChars;
    private BLEDiscoverState discoverBatteryChars;
    private BLEDiscoverState discoverDeviceInfoChars;

    private int clockMask = 0x1;

    private void updateDiscoverState(String x, BLEDiscoverState state) {
        if (x.matches("medwellchars")) {
            discoverDeviceChars = state;
        } else if (x.matches("batterychars")) {
            discoverBatteryChars = state;
        } else if (x.matches("deviceinfochars")) {
            discoverDeviceInfoChars = state;
        }
    }

    private boolean isDiscoverCharsDone() {
        if (enableFastConnect) {
            return discoverDeviceChars != BLEDiscoverState.BLE_DISCOVER_PENDING &&
                    discoverBatteryChars != BLEDiscoverState.BLE_DISCOVER_PENDING;
        } else {
            return discoverDeviceChars != BLEDiscoverState.BLE_DISCOVER_PENDING &&
                    discoverBatteryChars != BLEDiscoverState.BLE_DISCOVER_PENDING &&
                    discoverDeviceInfoChars != BLEDiscoverState.BLE_DISCOVER_PENDING;
        }
    }

    private Handler GattHandler;
    static final int MSG_GATT_CONNECT = 0;
    static final int MSG_GATT_ON_CONNECTION_STATE_CHANGE = 20;
    static final int MSG_GATT_ON_SERVICES_DISCOVERED = 21;
    static final int MSG_GATT_ON_CHARACTERISTIC_READ = 22;
    static final int MSG_GATT_ON_CHARACTERISTIC_WRITE = 23;
    static final int MSG_GATT_ON_CHARACTERISTIC_CHANGED = 27;
    static final int MSG_GATT_ON_DESCRIPTOR_WRITE = 28;

    private final IBinder mBinder = new LocalBinder();

    public class LocalBinder extends Binder {
        @SuppressWarnings("unused")
        public MedWellManagerService getService() {
            // Return this instance of MedWellManagerService so clients can call public methods
            return MedWellManagerService.this;
        }
    }

    @Override
    public IBinder onBind(Intent intent) {
        return mBinder;
    }

    @Override
    public boolean onUnbind(Intent intent) {
        // After using a given device, you should make sure that BluetoothGatt.close() is called
        // such that resources are cleaned up properly.  In this particular example, close() is
        // invoked when the UI is disconnected from the Service.
        // handled by the sub class - AEP - close();
        return super.onUnbind(intent);
    }

    private static Timer timer;

    private class mainTask extends TimerTask {
        public void run() {
            timerHandler.sendEmptyMessage(0);
        }
    }

    private static class TimerHandler extends Handler {
        private final WeakReference<MedWellManagerService> mService;

        TimerHandler(MedWellManagerService service) {
            mService = new WeakReference<>(service);
        }

        @Override
        public void handleMessage(Message msg) {
            MedWellManagerService service = mService.get();
            if (service != null) {
                Log.i(TAG, "Timer fired!!!!!!!!!!!!!!!!!!");
                service.timeoutManagerState();
            }
        }
    }

    private final TimerHandler timerHandler = new TimerHandler(this);

    public void updateManagerState(BLEManagerState newState) {

        boolean enableTimer = false;
        long timeoutInterval = 0;

        if (timer != null) {
            timer.cancel();
            timer = null;
        }

        switch (mConnectState) {
            case BLE_STANDBY:
                break;
            case BLE_DISCOVER_PERIPHERAL:
                break;
            case BLE_REQUEST_CONNECTION:
                if (newState == BLEManagerState.BLE_REQUEST_CONNECTION) {
                    timeoutInterval = 30000;
                    enableTimer = true;
                } else if (newState == BLEManagerState.BLE_DISCOVER_SERVICES) {
                    timeoutInterval = 30000;
                    enableTimer = true;
                }
                break;
            case BLE_DISCOVER_SERVICES:
                if (newState == BLEManagerState.BLE_DISCOVER_SERVICES) {
                    timeoutInterval = 30000;
                    enableTimer = true;
                } else if (newState == BLEManagerState.BLE_DISCOVER_CHARACTERISTICS) {
                    timeoutInterval = 30000;
                    enableTimer = true;
                }
                break;
            case BLE_DISCOVER_CHARACTERISTICS:
                if (newState == BLEManagerState.BLE_DISCOVER_CHARACTERISTICS) {
                    timeoutInterval = 30000;
                    enableTimer = true;
                } else if (newState == BLEManagerState.BLE_INITIALIZE) {
                    timeoutInterval = 30000;
                    enableTimer = true;
                }
                break;
            case BLE_INITIALIZE:
            case BLE_CONNECTED:
                mReadBlocksState = BLEReadBlocksState.BLE_READ_BLOCKS_IDLE;
                break;
            default:
                break;
        }

        BLEManagerState lastState = mConnectState;
        mConnectState = newState;

        if (enableTimer) {
            timer = new Timer();
            timer.schedule(new mainTask(), timeoutInterval);
        } else {
            switch (lastState) {
                case BLE_STANDBY:
                    break;
                case BLE_DISCOVER_PERIPHERAL:
                    break;
                case BLE_REQUEST_CONNECTION:
                case BLE_DISCOVER_SERVICES:
                case BLE_DISCOVER_CHARACTERISTICS:
                case BLE_INITIALIZE:
                    if (mConnectState == BLEManagerState.BLE_STANDBY) {
                        readQueue.clear();
                        writeQueue.clear();
                        if (mCallback != null)
                            mCallback.didCompleteConnect(false);
                    } else if (mConnectState == BLEManagerState.BLE_CONNECTED) {
                        connectionCompleteInitialization();
                        if (mCallback != null)
                            mCallback.didCompleteConnect(true);
                    }
                    break;
                case BLE_CONNECTED:
                    if (mConnectState == BLEManagerState.BLE_STANDBY) {
                        if (mCallback != null)
                            mCallback.didDisconnect();
                    }
                    break;
            }
        }
    }

    protected void updateReadBlocksState(BLEReadBlocksState newState) {
        if (mConnectState != BLEManagerState.BLE_CONNECTED) {
            mReadBlocksState = BLEReadBlocksState.BLE_READ_BLOCKS_IDLE;
            return;
        }

        boolean enableTimer = false;
        long timeoutInterval = 0;

        if (timer != null) {
            timer.cancel();
            timer = null;
        }

        switch (mReadBlocksState) {
            case BLE_READ_BLOCKS_IDLE:
                enableTimer = false;
                break;
            case BLE_READ_BLOCKS_OK:
                enableTimer = false;
                break;
            case BLE_READ_BLOCKS_TIMEOUT:
                enableTimer = false;
                break;
            case BLE_READ_BLOCKS_ENABLE:
                if (newState == BLEReadBlocksState.BLE_READ_BLOCKS_ENABLE) {
                    timeoutInterval = 2000;
                    enableTimer = true;
                }
                break;
            default:
                break;
        }

        mReadBlocksState = newState;

        if (enableTimer) {
            timer = new Timer();
            timer.schedule(new mainTask(), timeoutInterval);
        }

        // exec delegate method on change to ok or timeout
        if (mReadBlocksState == BLEReadBlocksState.BLE_READ_BLOCKS_OK) {
            if (mCallback != null)
                mCallback.didCompleteRead(true);
        } else if (mReadBlocksState == BLEReadBlocksState.BLE_READ_BLOCKS_TIMEOUT) {
            if (mCallback != null)
                mCallback.didCompleteRead(false);
        }
    }

    public void connectionCompleteInitialization() {

    }

    protected void processBlock(byte[] currData) {
        Log.i(TAG, "processBlock: super");
    }

    private void timeoutManagerState() {
        if (mConnectState == BLEManagerState.BLE_REQUEST_CONNECTION) {
            Log.i(TAG, "Request Connection Timeout");
            updateManagerState(BLEManagerState.BLE_STANDBY);
        } else if (mConnectState == BLEManagerState.BLE_DISCOVER_SERVICES) {
            Log.i(TAG, "Discover Services Timeout");
            updateManagerState(BLEManagerState.BLE_STANDBY);
        } else if (mConnectState == BLEManagerState.BLE_DISCOVER_CHARACTERISTICS) {
            Log.i(TAG, "Discover Characteristics Timeout");
            updateManagerState(BLEManagerState.BLE_STANDBY);
        } else if (mConnectState == BLEManagerState.BLE_INITIALIZE) {
            Log.i(TAG, "Initialization Timeout");
            updateManagerState(BLEManagerState.BLE_STANDBY);
        } else if (mConnectState == BLEManagerState.BLE_CONNECTED && mReadBlocksState == BLEReadBlocksState.BLE_READ_BLOCKS_ENABLE) {
            /* Read Blocks timeout */
            Log.i(TAG, "Read Blocks Timeout");
            updateReadBlocksState(BLEReadBlocksState.BLE_READ_BLOCKS_TIMEOUT);
        } else {
            Log.i(TAG, "Unknown Timeout Timeout");
        }
    }

    public boolean initialize(MedWellManagerServiceCallback callback) {
        mCallback = callback;

        mClockChar = null;
        mTestProgramChar = null;
        mReadBlockIndexChar = null;
        mBlockChar = null;
        mStatusChar = null;
        mWriteChar = null;
        discoverBatteryChars = BLEDiscoverState.BLE_DISCOVER_IDLE;
        discoverDeviceChars = BLEDiscoverState.BLE_DISCOVER_IDLE;
        discoverDeviceInfoChars = BLEDiscoverState.BLE_DISCOVER_IDLE;

        // For API level 18 and above, get a reference to BluetoothAdapter through
        // BluetoothManager.
        if (mBluetoothManager == null) {
            mBluetoothManager = (BluetoothManager) getSystemService(Context.BLUETOOTH_SERVICE);
            if (mBluetoothManager == null) {
                Log.e(TAG, "Unable to initialize BluetoothManager.");
                return false;
            }
        }

        mBluetoothAdapter = mBluetoothManager.getAdapter();
        if (mBluetoothAdapter == null) {
            Log.e(TAG, "Unable to obtain a BluetoothAdapter.");
            return false;
        }
        HandlerThread mThread = new HandlerThread(TAG, Process.THREAD_PRIORITY_FOREGROUND);
        mThread.start();
        Looper mgrLooper = mThread.getLooper();
        GattHandler = new GattCallbackHandler(mgrLooper);

        return true;
    }

    private void saveTotalConnectTime() {
        final SharedPreferences sharedPrefs = this.getApplicationContext().getSharedPreferences(Constants.PREFS_NAME, MODE_PRIVATE);
        sharedPrefs.edit()
                .putLong("TotalConnectTime", totalConnectTime)
                .putInt("TotalConnections", connections)
                .putLong("LastConnectTime", connectTime)
                .apply();
        Intent RTReturn = new Intent(MainActivity.RECEIVE_MSG);
        LocalBroadcastManager.getInstance(this).sendBroadcast(RTReturn);
    }

    private long getTotalConnectTime() {
        final SharedPreferences sharedPrefs = this.getApplicationContext().getSharedPreferences(Constants.PREFS_NAME, MODE_PRIVATE);
        connections = sharedPrefs.getInt("TotalConnections", 1);
        return sharedPrefs.getLong("TotalConnectTime", 0);
    }

    public void connect(final String address) {
        GattHandler.obtainMessage(MSG_GATT_CONNECT, address).sendToTarget();
    }

    public int queueSize() {
        return writeQueue.size() + readQueue.size();
    }

    public void disconnect() {
        if (mBluetoothAdapter == null || mBluetoothGatt == null) {
            Log.i(TAG, "BluetoothAdapter not initialized - disconnect()");
            return;
        }
        mBluetoothGatt.disconnect();
    }

    public void close() {
        if (mBluetoothGatt == null) {
            return;
        }
        mBluetoothGatt.close();
        mBluetoothGatt = null;
    }

    public void writeCharacteristic(BluetoothGattCharacteristic characteristic, byte[] data) {
        if (mBluetoothAdapter == null || mBluetoothGatt == null) {
            Log.i(TAG, "BluetoothAdapter not initialized - writeCharacteristic(BluetoothGattCharacteristic characteristic, byte [] data)");
            return;
        }

        if (characteristic == null) {
            Log.i(TAG, "Request write of null characteristic");
            return;
        }

        WriteCharacteristicData writeData = new WriteCharacteristicData();
        writeData.characteristic = characteristic;
        writeData.data = new byte[data.length];
        System.arraycopy(data, 0, writeData.data, 0, data.length);

        if (writeQueue.size() > 0) {
            writeQueue.add(writeData);
        } else {
            writeData.characteristic.setValue(writeData.data);
            writeQueue.add(writeData);
            mBluetoothGatt.writeCharacteristic(writeData.characteristic);
            if (logReadBlocks)
                Log.i(TAG, "writing data " + byteToHexString(writeData.data));
        }
    }

    public void readCharacteristic(BluetoothGattCharacteristic characteristic) {
        if (mBluetoothAdapter == null || mBluetoothGatt == null) {
            Log.i(TAG, "BluetoothAdapter not initialized - readCharacteristic(BluetoothGattCharacteristic characteristic)");
            return;
        }

        if (characteristic == null) {
            Log.i(TAG, "Request read of null characteristic");
            return;
        }

        if (readQueue.size() > 0) {
            readQueue.add(characteristic);
        } else {
            readQueue.add(characteristic);
            mBluetoothGatt.readCharacteristic(characteristic);
        }
        //Log.index(TAG, "readQueue push size " + readQueue.size());
    }

    public void readClock() {
        readCharacteristic(mClockChar);
    }

    public void readStatus() {
        readCharacteristic(mStatusChar);
    }

    public void writeTestProgram(byte[] data) {
        writeCharacteristic(mTestProgramChar, data);
    }

    public void readBatteryLevel() {
        readCharacteristic(mBatteryLevelChar);
    }

    public void readBlock(int index) {
        if (mConnectState == BLEManagerState.BLE_CONNECTED && mReadBlockIndexChar != null) {
            if (index < (deviceBlocks.length * 2)) {
                byte[] currIndexArray = new byte[4];

                mReadBlocksState = BLEReadBlocksState.BLE_READ_BLOCKS_ENABLE;
                convertIntToByteArray(index, currIndexArray, 0);
                writeCharacteristic(mReadBlockIndexChar, currIndexArray);
            }
        }
    }

    public void writeBlock(int index, byte[] data) {
        if (mConnectState == BLEManagerState.BLE_CONNECTED) {
            if (mBlockChar != null) {
                byte[] blockArray = new byte[20];
                byte[] cipherOut = new byte[16];
                int address;

                address = (index & 0xFFF) | 0xA000;

                blockArray[0] = (byte) (address & 0xFF);
                blockArray[1] = (byte) ((address >> 8) & 0xFF);
                blockArray[2] = 0;
                blockArray[3] = 0;

                if (enableSecurity) {
                    securitySessionKey[12] = 0x00;
                    securitySessionKey[13] = 0x00;
                    securitySessionKey[14] = 0x00;
                    securitySessionKey[15] = 0x00;
                    encryptBlock(securityInitKey, data, cipherOut);
                    for (int i = 0; i < 16; i++) {
                        blockArray[i + 4] = cipherOut[i];
                        // HACK: currently reminders are sent in the clear...
                        blockArray[i + 4] = data[i];
                    }
                } else {
                    System.arraycopy(data, 0, blockArray, 4, 16);
                }
                writeCharacteristic(mBlockChar, blockArray);
            }
        }
    }

    private void writeClock(long clock) {
        if (mConnectState == BLEManagerState.BLE_INITIALIZE) {
            if (mClockChar != null) {
                byte[] clockArray = new byte[4];
                convertIntToByteArray((int) (0xFFFFFFFFL & clock), clockArray, 0);
                writeCharacteristic(mClockChar, clockArray);
            }
        }
    }

    public void syncClock() {
        long currTime = System.currentTimeMillis() / 1000;
        writeClock(currTime);
    }

    private final BluetoothGattCallback mGattCallback = new BluetoothGattCallback() {
        @Override
        public void onConnectionStateChange(BluetoothGatt gatt, int status, int newState) {
            super.onConnectionStateChange(gatt, status, newState);
            GattHandler.obtainMessage(MSG_GATT_ON_CONNECTION_STATE_CHANGE, status, newState, gatt).sendToTarget();
        }
        @Override
        public void onServicesDiscovered(BluetoothGatt gatt, int status) {
            super.onServicesDiscovered(gatt, status);
            GattHandler.obtainMessage(MSG_GATT_ON_SERVICES_DISCOVERED, status, 0, gatt).sendToTarget();
        }
        @Override
        public void onCharacteristicRead(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic, int status) {
            super.onCharacteristicRead(gatt, characteristic, status);
            GattHandler.obtainMessage(MSG_GATT_ON_CHARACTERISTIC_READ, status, 0, characteristic).sendToTarget();
        }
        @Override
        public void onCharacteristicChanged(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic) {
            super.onCharacteristicChanged(gatt, characteristic);
            GattHandler.obtainMessage(MSG_GATT_ON_CHARACTERISTIC_CHANGED, characteristic).sendToTarget();
        }
        @Override
        public void onCharacteristicWrite(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic, int status) {
            super.onCharacteristicWrite(gatt, characteristic, status);
            GattHandler.obtainMessage(MSG_GATT_ON_CHARACTERISTIC_WRITE, status, 0, characteristic).sendToTarget();
        }
        @Override
        public void onDescriptorWrite(BluetoothGatt gatt, BluetoothGattDescriptor descriptor, int status) {
            super.onDescriptorWrite(gatt, descriptor, status);
            GattHandler.obtainMessage(MSG_GATT_ON_DESCRIPTOR_WRITE, status, 0, descriptor).sendToTarget();
        }
    };

    public class GattCallbackHandler extends Handler {
        private GattCallbackHandler(Looper looper) {
            super(looper);
        }
        @Override
        public void handleMessage(Message msg) {
            int status = msg.arg1;
            switch (msg.what) {
                case MSG_GATT_CONNECT:
                    if (!GattConnect((String)msg.obj)) {
                         if (mCallback != null)
                            mCallback.didCompleteConnect(false);
                    }
                    break;
                case MSG_GATT_ON_CONNECTION_STATE_CHANGE:
                    gattOnConnectionStateChange((BluetoothGatt) msg.obj, status, msg.arg2);
                    break;
                case MSG_GATT_ON_SERVICES_DISCOVERED:
                    gattOnServicesDiscovered(status);
                    break;
                case MSG_GATT_ON_CHARACTERISTIC_READ:
                    gattOnCharacteristicRead((BluetoothGattCharacteristic) msg.obj, status);
                    break;
                case MSG_GATT_ON_CHARACTERISTIC_WRITE:
                    gattOnCharacteristicWrite((BluetoothGattCharacteristic) msg.obj, status);
                    break;
                case MSG_GATT_ON_CHARACTERISTIC_CHANGED:
                    gattOnCharacteristicChanged((BluetoothGattCharacteristic) msg.obj);
                    break;
                case MSG_GATT_ON_DESCRIPTOR_WRITE:
                    gattOnDescriptorWrite((BluetoothGattDescriptor) msg.obj, status);
                    break;
                default:
                    Log.d(TAG, "Unimplemented msg_id=" + String.valueOf(msg.what));
                    break;
            }
        }
    }

    @GattHandlerThread
    private boolean GattConnect(String address) {
        if (mBluetoothAdapter == null || address == null || address.isEmpty()) {
            Log.e(TAG, "BluetoothAdapter not initialized or unspecified address.");
            return false;
        }
        if (mCallback != null)
            mCallback.didStartConnect();

        // Previously connected device.  Try to reconnect.
        if (mBluetoothGatt != null && address.equals(mBluetoothGatt.getDevice().getAddress())) {
            Log.i(TAG, "Trying to use an existing mBluetoothGatt for connection.");
            updateManagerState(BLEManagerState.BLE_REQUEST_CONNECTION);
            if (mBluetoothGatt.connect()) {
                Log.i(TAG, "re-connect success");
                return true;
            } else {
                Log.i(TAG, "re-connect fail");
                updateManagerState(BLEManagerState.BLE_STANDBY);
                return false;
            }
        }

        final BluetoothDevice device = mBluetoothAdapter.getRemoteDevice(address);
        if (device == null) {
            Log.i(TAG, "Device not found.  Unable to connect.");
            updateManagerState(BLEManagerState.BLE_STANDBY);
            return false;
        }
        // We want to directly connect to the device, so we are setting the autoConnect
        // parameter to false.
        readQueue.clear();
        connectionSecure = false;
        failedAuthentication = false;

        connectTime = System.currentTimeMillis();
        totalConnectTime = getTotalConnectTime();
        mBluetoothGatt = device.connectGatt(ZewaMedWellApp.getAppContext(), false, mGattCallback, BluetoothDevice.TRANSPORT_LE);
        Log.i(TAG, "Trying to create a new connection.");
        updateManagerState(BLEManagerState.BLE_REQUEST_CONNECTION);
        return true;
    }
    @GattHandlerThread
    private void gattOnConnectionStateChange(BluetoothGatt gatt, int status, int newState) {
        Log.i(TAG, "onConnectionStateChange - status == " + status);
        Log.i(TAG, "onConnectionStateChange - newState == " + newState);
        mBluetoothGatt = gatt;
        ble133ErrorCount += (status == 133 ? 1 : 0);
        if (newState == BluetoothProfile.STATE_CONNECTED && status == 0) {
            ble133ErrorCount = 0;
            connections++;
            lastConnectTime = System.currentTimeMillis() - connectTime;
            totalConnectTime += lastConnectTime;
            saveTotalConnectTime();
            Log.i(TAG, "onConnectionStateChange: connectTime: "
                    + String.valueOf((System.currentTimeMillis() - connectTime))
                    + "ms avgConnectTime: " + String.valueOf((float) (totalConnectTime / connections) / 1000) + "s");
            initBlocks();
            if (mCallback != null)
                mCallback.didConnect();

            updateDiscoverState("medwellserv", BLEDiscoverState.BLE_DISCOVER_PENDING);
            updateDiscoverState("batteryserv", BLEDiscoverState.BLE_DISCOVER_PENDING);
            updateDiscoverState("deviceinfoserv", BLEDiscoverState.BLE_DISCOVER_PENDING);

            updateManagerState(BLEManagerState.BLE_DISCOVER_SERVICES);

            Log.i(TAG, "Connected to GATT server.");
            Log.i(TAG, "Attempting to start service discovery:" +
                    mBluetoothGatt.discoverServices());

        } else if (newState == BluetoothProfile.STATE_DISCONNECTED || (newState == BluetoothProfile.STATE_CONNECTED && status == 133)) {
            if (newState == BluetoothProfile.STATE_CONNECTED)
                disconnect();
            close();
            mClockChar = null;
            mTestProgramChar = null;
            mReadBlockIndexChar = null;
            mBlockChar = null;
            mStatusChar = null;
            mWriteChar = null;

            Log.i(TAG, "Disconnected from GATT server.  status == " + status);
            if (status == 133)
                SystemClock.sleep(500);
            if (ble133ErrorCount > 3) {
                // turn off bluetooth.
                final BluetoothManager btMgr = (BluetoothManager) this.getApplicationContext().getSystemService(Context.BLUETOOTH_SERVICE);
                final BluetoothAdapter btAdapter;
                if (btMgr != null) {
                    btAdapter = btMgr.getAdapter();
                    if (btAdapter.isEnabled()) {
                        Log.d(TAG, "Turning off BluetoothAdapter");
                        btAdapter.disable();
                    }
                }
            } else {
                updateManagerState(BLEManagerState.BLE_STANDBY);
            }
        } else if (status == 133) {
            disconnect();
        }
    }
    @GattHandlerThread
    private void gattOnServicesDiscovered(int status) {
        Log.i(TAG, "gattOnServicesDiscovered: status: " + String.valueOf(status));
        if (status == BluetoothGatt.GATT_SUCCESS) {
            updateManagerState(BLEManagerState.BLE_DISCOVER_CHARACTERISTICS);
            for (BluetoothGattService gattService : mBluetoothGatt.getServices()) {
                if (UUID.fromString(BLE_SERVICE_UUID).equals(gattService.getUuid())) {
                    updateDiscoverState("medwellserv", BLEDiscoverState.BLE_DISCOVER_DONE);
                    updateDiscoverState("medwellchars", BLEDiscoverState.BLE_DISCOVER_PENDING);
                    for (BluetoothGattCharacteristic gattCharacteristic : gattService.getCharacteristics()) {
                        updateDiscoverState("medwellchars", BLEDiscoverState.BLE_DISCOVER_DONE);
                        if (UUID.fromString(BLE_CLOCK_UUID).equals(gattCharacteristic.getUuid())) {
                            mClockChar = gattCharacteristic;
                            Log.i(TAG, "Found Clock Characteristic");
                        } else if (UUID.fromString(BLE_TEST_PROGRAM_UUID).equals(gattCharacteristic.getUuid())) {
                            mTestProgramChar = gattCharacteristic;
                            Log.i(TAG, "Found Test/Program (DFU) Characteristic");
                        } else if (UUID.fromString(BLE_READ_BLOCK_INDEX_UUID).equals(gattCharacteristic.getUuid())) {
                            mReadBlockIndexChar = gattCharacteristic;
                            Log.i(TAG, "Found Read Block Index Characteristic");
                        } else if (UUID.fromString(BLE_BLOCK_UUID).equals(gattCharacteristic.getUuid())) {
                            mBlockChar = gattCharacteristic;
                            Log.i(TAG, "Found Block Characteristic");
                        } else if (UUID.fromString(BLE_STATUS_UUID).equals(gattCharacteristic.getUuid())) {
                            mStatusChar = gattCharacteristic;
                            Log.i(TAG, "Found Status Characteristic");
                        } else if (UUID.fromString(BLE_WRITE_UUID).equals(gattCharacteristic.getUuid())) {
                            mWriteChar = gattCharacteristic;
                            Log.i(TAG, "Found Write Characteristic");
                        }
                    }
                } else if (UUID.fromString(DEVICE_INFO_SERVICE_UUID).equals(gattService.getUuid())) {
                    updateDiscoverState("deviceinfoserv", BLEDiscoverState.BLE_DISCOVER_DONE);
                    updateDiscoverState("deviceinfochars", BLEDiscoverState.BLE_DISCOVER_PENDING);
                    for (BluetoothGattCharacteristic gattCharacteristic : gattService.getCharacteristics()) {
                        updateDiscoverState("deviceinfochars", BLEDiscoverState.BLE_DISCOVER_DONE);
                        if (UUID.fromString(DEVICE_INFO_SYSTEM_ID_UUID).equals(gattCharacteristic.getUuid())) {
                            mSystemIdNumberChar = gattCharacteristic;
                            Log.i(TAG, "Found System Id Number Characteristic");
                        } else if (UUID.fromString(DEVICE_INFO_MODEL_NUM_UUID).equals(gattCharacteristic.getUuid())) {
                            mModelNumberChar = gattCharacteristic;
                            Log.i(TAG, "Found Model Number Characteristic");
                        } else if (UUID.fromString(DEVICE_INFO_SERIAL_NUM_UUID).equals(gattCharacteristic.getUuid())) {
                            mSerialNumberChar = gattCharacteristic;
                            Log.i(TAG, "Found Serial Number Characteristic");
                        } else if (UUID.fromString(DEVICE_INFO_FW_REV_UUID).equals(gattCharacteristic.getUuid())) {
                            mFirmwareRevisionChar = gattCharacteristic;
                            Log.i(TAG, "Found Firmware Revision Characteristic");
                        } else if (UUID.fromString(DEVICE_INFO_HW_REV_UUID).equals(gattCharacteristic.getUuid())) {
                            mHardwareRevisionChar = gattCharacteristic;
                            Log.i(TAG, "Found Hardware Revision Characteristic");
                        } else if (UUID.fromString(DEVICE_INFO_SW_REV_UUID).equals(gattCharacteristic.getUuid())) {
                            mSoftwareRevisionChar = gattCharacteristic;
                            Log.i(TAG, "Found Software Revision Characteristic");
                        } else if (UUID.fromString(DEVICE_INFO_MANF_NAME_UUID).equals(gattCharacteristic.getUuid())) {
                            mManufacturerChar = gattCharacteristic;
                            Log.i(TAG, "Found a Device Manufacturer Name Characteristic");
                        }
                    }
                } else if (UUID.fromString(BATTERY_SERVICE_UUID).equals(gattService.getUuid())) {
                    updateDiscoverState("batteryserv", BLEDiscoverState.BLE_DISCOVER_DONE);
                    updateDiscoverState("batterychars", BLEDiscoverState.BLE_DISCOVER_PENDING);
                    for (BluetoothGattCharacteristic gattCharacteristic : gattService.getCharacteristics()) {
                        updateDiscoverState("batterychars", BLEDiscoverState.BLE_DISCOVER_DONE);
                        if (UUID.fromString(BATTERY_LEVEL_UUID).equals(gattCharacteristic.getUuid())) {
                            mBatteryLevelChar = gattCharacteristic;
                            Log.i(TAG, "Found Battery Level Characteristic");
                        }
                    }
                }
            }

            if (isDiscoverCharsDone()) {
                if (mConnectState != BLEManagerState.BLE_INITIALIZE) {
                    updateManagerState(BLEManagerState.BLE_INITIALIZE);

                    initReadFlag = 0x00;

                    GattHandler.postDelayed(new Runnable() {
                        @Override
                        public void run() {
                            readClock();
                            readStatus();
                            readCharacteristic(mSystemIdNumberChar);
                            readCharacteristic(mWriteChar);
                            readBatteryLevel();
                            if (!enableFastConnect) {
                                readCharacteristic(mModelNumberChar);
                                readCharacteristic(mSerialNumberChar);
                                readCharacteristic(mFirmwareRevisionChar);
                                readCharacteristic(mHardwareRevisionChar);
                                readCharacteristic(mSoftwareRevisionChar);
                                readCharacteristic(mManufacturerChar);
                                readCharacteristic(mTestProgramChar);
                            }
                        }
                    }, 750);

                }
            }
        }
    }
    @GattHandlerThread
    private void gattOnCharacteristicRead(BluetoothGattCharacteristic characteristic, int status) {
        int statusMask = 0x1 << 1;
        int batteryLevelMask = 0x1 << 2;
        int modelNumberMask = 0x1 << 3;
        int serialNumberMask = 0x1 << 4;
        int firmwareRevisionMask = 0x1 << 5;
        int hardwareRevisionMask = 0x1 << 6;
        int softwareRevisionMask = 0x1 << 7;
        int manufacturerMask = 0x1 << 8;
        @SuppressWarnings("unused")
        int deviceNameMask = 0x1 << 9;
        int connectionInfoMask = 0x1 << 10;
        int systemIdNumberMask = 0x1 << 11;
        int writeBlockMask = 0x1 << 12;
        if (status == BluetoothGatt.GATT_SUCCESS) {
            byte[] data;
            if (UUID.fromString(BLE_CLOCK_UUID).equals(characteristic.getUuid())) {
                data = characteristic.getValue();
                if (data.length == 4) {
                    deviceClock = convertByteArrayToInt(data, 0);
                    deviceClockDriftOnConnect = (deviceClock - (System.currentTimeMillis() / 1000));
                    Log.i(TAG, "Read clock = " + deviceClock + " - " + byteToHexString(data) + " - Drift: " + deviceClockDriftOnConnect);
                    initReadFlag |= clockMask;
                    if (mCallback != null)
                        mCallback.didReadClock(deviceClock);
                }
            } else if (UUID.fromString(BLE_TEST_PROGRAM_UUID).equals(characteristic.getUuid())) {
                data = characteristic.getValue();
                if (data.length == 4) {
                    connectionInterval = data[0];
                    Log.i(TAG, "Test/Program = " + connectionInterval);
                    initReadFlag |= connectionInfoMask;
                }
            } else if (UUID.fromString(BLE_BLOCK_UUID).equals(characteristic.getUuid())) {
                data = characteristic.getValue();
                if (data.length == 20) {
                    System.arraycopy(data, 0, readBlockData, 0, data.length);
                    Log.i(TAG, "read block: " + byteToHexString(data));
                }
            } else if (UUID.fromString(BLE_STATUS_UUID).equals(characteristic.getUuid())) {
                data = characteristic.getValue();
                if (data.length == 20) {
                    if (connectionSecure && enableSecurity && !storeEncryptedData) {
                        byte[] statusCipher = new byte[16];
                        byte[] statusClear = new byte[16];
                        byte[] decryptOut = new byte[20];

                        securitySessionKey[12] = data[0];
                        securitySessionKey[13] = data[1];
                        securitySessionKey[14] = data[2];
                        securitySessionKey[15] = data[3];

                        System.arraycopy(data, 4, statusCipher, 0, 16);

                        decryptBlock(securityInitKey, statusCipher, statusClear);

                        for (int i = 0; i < 20; i++) {
                            if (i < 4)
                                decryptOut[i] = data[i];
                            else
                                decryptOut[i] = statusClear[i - 4];
                        }

                        System.arraycopy(decryptOut, 0, statusBlockData, 0, statusBlockData.length);
                    } else {
                        System.arraycopy(data, 0, statusBlockData, 0, data.length);
                    }
                    Log.i(TAG, "Read Status: " + byteToHexString(statusBlockData));
                    initReadFlag |= statusMask;

                    // need to shift into connected mode now...
                    if (connectionSecure && enableSecurity && mConnectState == BLEManagerState.BLE_INITIALIZE)
                        updateManagerState(BLEManagerState.BLE_CONNECTED);
                }
            } else if (UUID.fromString(BLE_WRITE_UUID).equals(characteristic.getUuid())) {
                data = characteristic.getValue();
                if (data.length == 20) {
                    System.arraycopy(data, 0, writeBlockData, 0, writeBlockData.length);
                    Log.i(TAG, "Write Block: " + byteToHexString(writeBlockData));
                    initReadFlag |= writeBlockMask;
                }
            } else if (UUID.fromString(BATTERY_LEVEL_UUID).equals(characteristic.getUuid())) {
                data = characteristic.getValue();
                if (data.length == 1) {
                    batteryLevel = data[0];
                    Log.i(TAG, "Battery Level = " + batteryLevel);
                    initReadFlag |= batteryLevelMask;
                }
            } else if (UUID.fromString(DEVICE_INFO_SYSTEM_ID_UUID).equals(characteristic.getUuid())) {
                data = characteristic.getValue();
                StringBuilder sin = new StringBuilder();
                StringBuilder ha = new StringBuilder();
                if (data.length == 8) {
                    for (int i = 0; i < 8; i++) {
                        systemIdBytes[i] = data[i];
                        if (i < 7)
                            sin.append(String.format("%02X-", data[i]));
                        else
                            sin.append(String.format("%02X", data[i]));
                        if (i == 0)
                            ha.append(String.format("%02X", data[i]));
                        else if (i < 3 || i > 4)
                            ha.append(String.format("%02X:", data[i])).append(hardwareAddress);
                    }
                    systemIdNumber = sin.toString();
                    hardwareAddress = ha.toString();
                    Log.i(TAG, "System Id = " + systemIdNumber);
                    Log.i(TAG, "Hardware Address = " + hardwareAddress);
                    initReadFlag |= systemIdNumberMask;
                }
            } else if (UUID.fromString(DEVICE_INFO_MODEL_NUM_UUID).equals(characteristic.getUuid())) {
                data = characteristic.getValue();
                modelNumber = nullByteArrayToString(data);
                Log.i(TAG, "Model Number = " + modelNumber);
                initReadFlag |= modelNumberMask;
            } else if (UUID.fromString(DEVICE_INFO_SERIAL_NUM_UUID).equals(characteristic.getUuid())) {
                data = characteristic.getValue();
                serialNumber = nullByteArrayToString(data);
                Log.i(TAG, "Serial Number = " + serialNumber);
                initReadFlag |= serialNumberMask;
            } else if (UUID.fromString(DEVICE_INFO_FW_REV_UUID).equals(characteristic.getUuid())) {
                data = characteristic.getValue();
                firmwareRevision = nullByteArrayToString(data);
                Log.i(TAG, "Firmware Revision = " + firmwareRevision);
                initReadFlag |= firmwareRevisionMask;
            } else if (UUID.fromString(DEVICE_INFO_HW_REV_UUID).equals(characteristic.getUuid())) {
                data = characteristic.getValue();
                hardwareRevision = nullByteArrayToString(data);
                Log.i(TAG, "Hardware Revision = " + hardwareRevision);
                initReadFlag |= hardwareRevisionMask;
            } else if (UUID.fromString(DEVICE_INFO_SW_REV_UUID).equals(characteristic.getUuid())) {
                data = characteristic.getValue();
                softwareRevision = nullByteArrayToString(data);
                Log.i(TAG, "Software Revision = " + softwareRevision);
                initReadFlag |= softwareRevisionMask;
            } else if (UUID.fromString(DEVICE_INFO_MANF_NAME_UUID).equals(characteristic.getUuid())) {
                data = characteristic.getValue();
                manufacturer = nullByteArrayToString(data);
                Log.i(TAG, "Manufacturer Name = " + manufacturer);
                initReadFlag |= manufacturerMask;
            }

            //
            // process read queue
            //
            if (readQueue.size() > 0) {
                if (readQueue.peek() == characteristic) {
                    readQueue.poll();
                    if (readQueue.peek() != null) {
                        mBluetoothGatt.readCharacteristic(readQueue.peek());
                    }
                } else {
                    Log.i(TAG, "ERROR: readQueue no match " + characteristic.getUuid());
                }
            } else {
                Log.i(TAG, "ERROR: readQueue empty");
            }

            if (mConnectState == BLEManagerState.BLE_INITIALIZE) {
                if (!enableFastConnect) {
                    if (initReadFlag == (batteryLevelMask |
                            writeBlockMask |
                            systemIdNumberMask |
                            clockMask |
                            statusMask |
                            modelNumberMask |
                            serialNumberMask |
                            firmwareRevisionMask |
                            hardwareRevisionMask |
                            softwareRevisionMask |
                            manufacturerMask |
                            connectionInfoMask)) {
                        if (enableSecurity && !connectionSecure) {
                            // changing order - now enable notifications before sending the security challenge.
                            // block char.
                            enableNotifications(mBlockChar);
                        } else {
                            // now write the clock, since we just read it so we can calculate drift...
                            syncClock();
                        }

                    }
                } else {
                    if (initReadFlag == (batteryLevelMask |
                            clockMask |
                            statusMask |
                            writeBlockMask |
                            systemIdNumberMask |
                            connectionInfoMask)) {
                        if (enableSecurity && !connectionSecure) {
                            // changing order - now enable notifications before sending the security challenge.
                            // status char.
                            enableNotifications(mBlockChar);
                        } else {
                            // now write the clock, since we just read it so we can calculate drift...
                            syncClock();
                        }
                    }
                }
            }
        }
    }
    @GattHandlerThread
    private void gattOnCharacteristicWrite(BluetoothGattCharacteristic characteristic, int status) {
        //Log.index(TAG, "onCharacteristicWrite");
        if (UUID.fromString(BLE_CLOCK_UUID).equals(characteristic.getUuid())) {
            if (status == BluetoothGatt.GATT_SUCCESS) {
                Log.i(TAG, "Write Clock Done");
                //readStatus();
            }
        } else if (UUID.fromString(BLE_READ_BLOCK_INDEX_UUID).equals(characteristic.getUuid())) {
            if (status == BluetoothGatt.GATT_SUCCESS) {
                if (logReadBlocks)
                    Log.i(TAG, "Write Read Index Done");
            }
        } else if (UUID.fromString(BLE_WRITE_UUID).equals(characteristic.getUuid())) {
            if (status == BluetoothGatt.GATT_SUCCESS) {
                if (mConnectState == BLEManagerState.BLE_INITIALIZE) {
                    //Log.i(TAG, "Challenge Response Done");

                    connectionSecure = true;
                    Log.i(TAG, "Connection Secure");

                    initReadFlag &= ~(clockMask);
                    readClock();

                } else {
                    Log.i(TAG, "Write Block Done");
                    if (mCallback != null)
                        mCallback.didWriteBlock(true);
                }
            }
        } else if (UUID.fromString(BLE_BLOCK_UUID).equals(characteristic.getUuid())) {
            if (status == BluetoothGatt.GATT_SUCCESS) {
                if (logReadBlocks)
                    Log.i(TAG, "Write Block (reminder) Done");
            }
        } else if (UUID.fromString(BLE_TEST_PROGRAM_UUID).equals(characteristic.getUuid())) {
            if (status == BluetoothGatt.GATT_SUCCESS) {
                if (logReadBlocks)
                    Log.i(TAG, "Write Block (test/program) Done");
            }
        } else {
            Log.i(TAG, "unknown characteristic was written");
        }


        //
        // process write queue
        //
        if (writeQueue.size() > 0) {
            if (writeQueue.peek().characteristic == characteristic) {
                writeQueue.poll();
                if (writeQueue.peek() != null) {
                    if (logReadBlocks)
                        Log.i(TAG, "writing data " + byteToHexString(Objects.requireNonNull(writeQueue.peek()).data));
                    Objects.requireNonNull(writeQueue.peek()).characteristic.setValue(Objects.requireNonNull(writeQueue.peek()).data);
                    mBluetoothGatt.writeCharacteristic(Objects.requireNonNull(writeQueue.peek()).characteristic);
                }

            } else {
                Log.i(TAG, "ERROR: writeQueue no match " + characteristic.getUuid());
            }
        } else {
            Log.i(TAG, "ERROR: writeQueue empty");
        }
    }
    @GattHandlerThread
    private void gattOnCharacteristicChanged(BluetoothGattCharacteristic characteristic) {
        //Log.index(TAG, "onCharacteristicChanged");
        byte[] data;
        if (UUID.fromString(BLE_BLOCK_UUID).equals(characteristic.getUuid())) {
            if (mReadBlocksState == BLEReadBlocksState.BLE_READ_BLOCKS_ENABLE) {

                data = characteristic.getValue();
                if (data.length == 20) {
                    int index;

                    index = convertByteArrayToInt(data, 0);
                    if (index >= 0 && index < 4000) {

                        if (connectionSecure && enableSecurity && !storeEncryptedData) {
                            byte[] statusCipher = new byte[16];
                            byte[] statusClear = new byte[16];

                            System.arraycopy(data, 0, securitySessionKey, 12, 4);

                            System.arraycopy(data, 4, statusCipher, 0, 16);

                            decryptBlock(securityInitKey, statusCipher, statusClear);

                            System.arraycopy(statusClear, 0, data, 4, 16);
                        }
                        Log.i(TAG, "block changed: " + byteToHexString(data));
                        processBlock(data);
                    }
                }

            }
        } else if (UUID.fromString(BLE_STATUS_UUID).equals(characteristic.getUuid())) {
            data = characteristic.getValue();
            if (data.length == 20) {
                if (connectionSecure && enableSecurity && !storeEncryptedData) {
                    byte[] statusCipher = new byte[16];
                    byte[] statusClear = new byte[16];
                    byte[] decryptOut = new byte[20];

                    securitySessionKey[12] = data[0];
                    securitySessionKey[13] = data[1];
                    securitySessionKey[14] = data[2];
                    securitySessionKey[15] = data[3];

                    System.arraycopy(data, 4, statusCipher, 0, 16);
                    decryptBlock(securityInitKey, statusCipher, statusClear);

                    System.arraycopy(data, 0, decryptOut, 0, 4);
                    System.arraycopy(statusClear, 0, decryptOut, 4, 16);
                    System.arraycopy(decryptOut, 0, statusBlockData, 0, statusBlockData.length);
                } else {
                    System.arraycopy(data, 0, statusBlockData, 0, data.length);
                }
                Log.i(TAG, "Status Block Changed: " + byteToHexString(statusBlockData));

                // need to shift into connected mode now...
                if (connectionSecure && enableSecurity && mConnectState == BLEManagerState.BLE_INITIALIZE)
                    updateManagerState(BLEManagerState.BLE_CONNECTED);
            }
        }
    }
    @GattHandlerThread
    private void gattOnDescriptorWrite(BluetoothGattDescriptor descriptor, int status) {
        Log.i(TAG, "onDescriptorWrite");
        if (UUID.fromString(BLE_BLOCK_UUID).equals(descriptor.getCharacteristic().getUuid())) {
            if (status == BluetoothGatt.GATT_SUCCESS) {
                Log.i(TAG, "Block Characteristic notifications set");
                GattHandler.postDelayed(new Runnable() {
                    @Override
                    public void run() {
                        enableNotifications(mStatusChar);
                    }
                }, 750);
            }
        } else if (UUID.fromString(BLE_STATUS_UUID).equals(descriptor.getCharacteristic().getUuid())) {
            if (status == BluetoothGatt.GATT_SUCCESS) {
                Log.i(TAG, "Status Characteristic Notifications set");
                // now shift into secure mode.
                GattHandler.postDelayed(new Runnable() {
                    @Override
                    public void run() {
                        sendSecurityChallengeResponse();
                    }
                }, 750);
            }
        }
    }
    @GattHandlerThread
    private void enableNotifications(BluetoothGattCharacteristic mChar) {
        // kick off descriptor writes
        if (mChar == mStatusChar) {
            Log.i(TAG, "enableNotifications: Status Characteristic");
        } else if (mChar == mBlockChar) {
            Log.i(TAG, "enableNotifications: Block Characteristic");
        }
        mBluetoothGatt.setCharacteristicNotification(mChar, true);
        BluetoothGattDescriptor descriptor = mChar.getDescriptor(
                UUID.fromString(NOTIFY_DESCRIPTOR_UUID)); // NOTIFY_DESCRIPTOR_UUID = "00002902-0000-1000-8000-00805f9b34fb"
        descriptor.setValue(BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE); // BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE = 0x0100
        mBluetoothGatt.writeDescriptor(descriptor);
    }

    protected String byteToHexString(byte[] x) {
        StringBuilder y = new StringBuilder();

        for (byte c : x) {
            y.append(String.format("%02X ", c));
        }
        return y.toString();
    }

    protected String nullByteArrayToString(byte[] x) {
        byte y[] = new byte[x.length];
        System.arraycopy(x, 0, y, 0, x.length);
        return new String(y);

    }

    public int convertByteArrayToInt(byte[] x, int index) {
        int value = 0;
        for (int i = 0; i < 4; i++) {
            if (i + index < x.length) {
                value += ((int) x[i + index] & 0xFF) << (8 * i);
            }
        }
        return value;
    }

    public long convertByteArrayToHalfLong(byte[] x, int index) {
        long value = 0;
        for (int i = 0; i < 4; i++) {
            if (i + index < x.length) {
                value += ((long) x[i + index] & (long) 0xFF) << (8 * i);
            }
        }
        return value;
    }

    public void convertIntToByteArray(int x, byte[] y, int index) {
        if (index + 3 < y.length) {
            y[index] = (byte) (x & 0xFF);
            y[index + 1] = (byte) ((x >> 8) & 0xFF);
            y[index + 2] = (byte) ((x >> 16) & 0xFF);
            y[index + 3] = (byte) ((x >> 24) & 0xFF);
        }
    }

    public void setSecurityInitKey() {
        System.arraycopy(baseSecurityKey, 0, securityInitKey, 0, 8);
        System.arraycopy(systemIdBytes, 0, securityInitKey, 8, 8);
    }

    private void encryptBlock(byte[] key, byte[] clearIn, byte[] cipherOut) {
        Cipher cipher;
        SecretKeySpec encryptKey;
        byte[] blank = new byte[16];
        for (int i = 0; i < 16; i++) {
            blank[i] = 0;
        }
        IvParameterSpec ivSpec = new IvParameterSpec(blank);

        try {
            cipher = Cipher.getInstance("AES/CBC/NoPadding");
            encryptKey = new SecretKeySpec(key, "AES");
            cipher.init(Cipher.ENCRYPT_MODE, encryptKey, ivSpec);
            byte[] cipherText = cipher.doFinal(clearIn);

            System.arraycopy(cipherText, 0, cipherOut, 0, cipherText.length);
        } catch (Exception e) {
            // Auto-generated catch block
            e.printStackTrace();
        }
    }

    private void decryptBlock(byte[] key, byte[] cipherIn, byte[] clearOut) {
        Cipher cipher;
        SecretKeySpec decryptKey;
        byte[] blank = new byte[16];
        for (int i = 0; i < 16; i++) {
            blank[i] = 0;
        }
        IvParameterSpec ivSpec = new IvParameterSpec(blank);

        try {
            cipher = Cipher.getInstance("AES/CBC/NoPadding");
            decryptKey = new SecretKeySpec(key, "AES");
            cipher.init(Cipher.DECRYPT_MODE, decryptKey, ivSpec);
            byte[] clearText = cipher.doFinal(cipherIn);

            System.arraycopy(clearText, 0, clearOut, 0, clearText.length);
        } catch (Exception e) {
            // Auto-generated catch block
            e.printStackTrace();
        }
    }

    private void sendSecurityChallengeResponse() {
        if (mConnectState == BLEManagerState.BLE_INITIALIZE) {
            if (mWriteChar != null) {
                byte challengeClear[] = new byte[16];
                byte responseCipher[] = new byte[16];
                byte response[] = new byte[20];
                setSecurityInitKey();

                System.arraycopy(writeBlockData, 4, challengeClear, 0, 16);
                //Log.i(TAG, "Challenge: " + byteToHexString(challengeClear));

                encryptBlock(securityInitKey, challengeClear, responseCipher);

                //Log.i(TAG, "Response Cipher: " + byteToHexString(responseCipher));

                response[0] = (byte) 0xEE;
                response[1] = (byte) 0xEE;
                response[2] = (byte) 0xEE;
                response[3] = (byte) 0xEE;

                System.arraycopy(responseCipher, 0, response, 4, 16);

                writeCharacteristic(mWriteChar, response);
                //Log.index(TAG, "writeCharacteristic: " + byteToHexString(response));
            }
        }
    }

    @SuppressWarnings("unused")
    private void updateReadProgress() {
        if (mReadBlocksState == BLEReadBlocksState.BLE_READ_BLOCKS_ENABLE) {
            if (mCallback != null)
                mCallback.didUpdateReadProgress(readProgress);
        }
    }
}

