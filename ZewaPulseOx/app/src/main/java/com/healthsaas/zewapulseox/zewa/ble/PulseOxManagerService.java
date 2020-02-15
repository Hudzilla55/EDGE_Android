package com.healthsaas.zewapulseox.zewa.ble;

import android.annotation.SuppressLint;
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

import com.healthsaas.zewapulseox.Constants;
import com.healthsaas.zewapulseox.MainActivity;
import com.healthsaas.zewapulseox.ZewaPulseOxApp;
import com.healthsaas.zewapulseox.zewa.pulseox.SDKConstants;

import java.lang.ref.WeakReference;
import java.util.LinkedList;
import java.util.Queue;
import java.util.Timer;
import java.util.TimerTask;
import java.util.UUID;

import androidx.localbroadcastmanager.content.LocalBroadcastManager;

@TargetApi(23)
public abstract class PulseOxManagerService extends Service {
    private final static String TAG = PulseOxManagerService.class.getSimpleName();

    protected String BLE_SERVICE_UUID;
    protected String BLE_DATA_UUID;

    protected String NOTIFY_DESCRIPTOR_UUID = "00002902-0000-1000-8000-00805f9b34fb";

    public PulseOxManagerService() {
        BLE_SERVICE_UUID = SDKConstants.PULSEOX_SERVICE_UUID;
        BLE_DATA_UUID = SDKConstants.PULSEOX_DATA_UUID;
    }

    private PulseOxManagerServiceCallback mCallback;
    private BluetoothManager mBluetoothManager;
    private BluetoothAdapter mBluetoothAdapter;
    private BluetoothGatt mBluetoothGatt;

    private Queue<WriteCharacteristicData> writeQueue = new LinkedList<>();
    private class WriteCharacteristicData {
        BluetoothGattCharacteristic characteristic;
        byte[] data;
    }

    private BLEManagerState mConnectState = BLEManagerState.BLE_STANDBY;
    protected BLEReadBlocksState mReadBlocksState = BLEReadBlocksState.BLE_READ_BLOCKS_IDLE;

    long connectTime = 0;
    int connections = 0;
    long totalConnectTime = 0;
    long lastConnectTime = 0;
    int ble133ErrorCount = 0;

    public int batteryLevel;

    private enum BLEDiscoverState {
        BLE_DISCOVER_IDLE,
        BLE_DISCOVER_PENDING,
        BLE_DISCOVER_DONE
    }

    private BLEDiscoverState discoverDeviceChars;
    private void updateDiscoverState(String x, BLEDiscoverState state) {
        if (x.matches("pulseoxchars")) {
            discoverDeviceChars = state;
        }
    }

    private boolean isDiscoverCharsDone() {
        return discoverDeviceChars != BLEDiscoverState.BLE_DISCOVER_PENDING ;
    }

    private Handler GattHandler;
    static final int MSG_GATT_CONNECT = 0;
    static final int MSG_GATT_ON_CONNECTION_STATE_CHANGE = 20;
    static final int MSG_GATT_ON_SERVICES_DISCOVERED = 21;
    static final int MSG_GATT_ON_CHARACTERISTIC_CHANGED = 27;
    static final int MSG_GATT_ON_DESCRIPTOR_WRITE = 28;

    private final IBinder mBinder = new LocalBinder();

    public class LocalBinder extends Binder {
        @SuppressWarnings("unused")
        public PulseOxManagerService getService() {
            // Return this instance of PulseOxManagerService so clients can call public methods
            return PulseOxManagerService.this;
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
        private final WeakReference<PulseOxManagerService> mService;

        TimerHandler(PulseOxManagerService service) {
            mService = new WeakReference<>(service);
        }

        @Override
        public void handleMessage(Message msg) {
            PulseOxManagerService service = mService.get();
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

    public boolean initialize(PulseOxManagerServiceCallback callback) {
        mCallback = callback;

        discoverDeviceChars = BLEDiscoverState.BLE_DISCOVER_IDLE;

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
        public void onCharacteristicChanged(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic) {
            super.onCharacteristicChanged(gatt, characteristic);
            GattHandler.obtainMessage(MSG_GATT_ON_CHARACTERISTIC_CHANGED, characteristic).sendToTarget();
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
        connectTime = System.currentTimeMillis();
        totalConnectTime = getTotalConnectTime();
        mBluetoothGatt = device.connectGatt(ZewaPulseOxApp.getAppContext(), false, mGattCallback, BluetoothDevice.TRANSPORT_LE);
        Log.i(TAG, "Trying to create a new connection.");
        updateManagerState(BLEManagerState.BLE_REQUEST_CONNECTION);
        return true;
    }
    @SuppressLint("MissingPermission")
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
            if (mCallback != null)
                mCallback.didConnect();

            updateDiscoverState("pulseoxserv", BLEDiscoverState.BLE_DISCOVER_PENDING);
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
                    updateDiscoverState("pulseoxserv", BLEDiscoverState.BLE_DISCOVER_DONE);
                    updateDiscoverState("pulseoxchars", BLEDiscoverState.BLE_DISCOVER_PENDING);
                    for (BluetoothGattCharacteristic gattCharacteristic : gattService.getCharacteristics()) {
                        updateDiscoverState("pulseoxchars", BLEDiscoverState.BLE_DISCOVER_DONE);
                        if (UUID.fromString(BLE_DATA_UUID).equals(gattCharacteristic.getUuid())) {
                            Log.i(TAG, "Found Data Characteristic");
                            enableNotifications(gattCharacteristic);
                        }
                    }
                }
            }

            if (isDiscoverCharsDone()) {
                if (mConnectState != BLEManagerState.BLE_INITIALIZE) {
                    updateManagerState(BLEManagerState.BLE_CONNECTED);
                }
            }
        }
    }
    @GattHandlerThread
    private void gattOnCharacteristicChanged(BluetoothGattCharacteristic characteristic) {
        byte[] data;
        if (UUID.fromString(BLE_DATA_UUID).equals(characteristic.getUuid())) {
            data = characteristic.getValue();
            processBlock(data);
        }
    }
    @GattHandlerThread
    private void gattOnDescriptorWrite(BluetoothGattDescriptor descriptor, int status) {
        Log.i(TAG, "onDescriptorWrite");
        if (UUID.fromString(BLE_DATA_UUID).equals(descriptor.getCharacteristic().getUuid())) {
            if (status == BluetoothGatt.GATT_SUCCESS) {
                Log.i(TAG, "Data Characteristic notifications set");
            }
        }
    }
    @GattHandlerThread
    private void enableNotifications(BluetoothGattCharacteristic mChar) {
        // kick off descriptor writes
        Log.i(TAG, "enableNotifications: Data Characteristic");
        mBluetoothGatt.setCharacteristicNotification(mChar, true);
        BluetoothGattDescriptor descriptor = mChar.getDescriptor(
                UUID.fromString(NOTIFY_DESCRIPTOR_UUID)); // NOTIFY_DESCRIPTOR_UUID = "00002902-0000-1000-8000-00805f9b34fb"
        descriptor.setValue(BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE); // BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE = 0x0100
        mBluetoothGatt.writeDescriptor(descriptor);
    }
}

