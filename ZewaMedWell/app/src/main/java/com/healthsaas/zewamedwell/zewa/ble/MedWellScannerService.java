package com.healthsaas.zewamedwell.zewa.ble;

import android.annotation.TargetApi;
import android.app.Service;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothManager;
import android.bluetooth.le.BluetoothLeScanner;
import android.bluetooth.le.ScanCallback;
import android.bluetooth.le.ScanFilter;
import android.bluetooth.le.ScanResult;
import android.bluetooth.le.ScanSettings;
import android.content.Context;
import android.content.Intent;
import android.os.Binder;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.util.Log;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

@TargetApi(23)
public class MedWellScannerService extends Service {
	private final static String TAG = MedWellScannerService.class.getSimpleName();
	
	// default is MedWell
	protected String BLE_SERVICE_UUID;
	private boolean isScanning = false;
	private static final Object oLock = new Object();
    private boolean hasScanResults = false;
    private long nextNDLogTime = 0;
    private int numberOfNoDataHits = 0;

	public MedWellScannerService() {
		BLE_SERVICE_UUID = "abcdef00-1234-4679-0abc-0100500abcde";
	}
	
	//
	// variables
	//
	private MedWellScannerServiceCallback mCallback;
    private BluetoothManager mBluetoothManager;
    private BluetoothAdapter mBluetoothAdapter;
    private BluetoothLeScanner mBluetoothLeScanner;

	public ArrayList<BluetoothDevice> mPeripherals;
	public ArrayList<String> mDeviceMacs;
	public ArrayList<BLEAdvertisingData> mDeviceAdvertising;

	//
	// service binding stuff
	//
	private final IBinder mBinder = new LocalBinder();

    public class LocalBinder extends Binder {
        public MedWellScannerService getService() {
            // Return this instance of LocalService so clients can call public methods
            return MedWellScannerService.this;
        }
    }
    private final Handler mHandler = new Handler();

    @Override
    public IBinder onBind(Intent intent) {
        return mBinder;
    }

    @Override
    public boolean onUnbind(Intent intent) {
        // After using a given device, you should make sure that BluetoothGatt.close() is called
        // such that resources are cleaned up properly.  In this particular example, close() is
        // invoked when the UI is disconnected from the Service.
        //close();
        return super.onUnbind(intent);
    }

    //
    // methods for service interaction
    //
    public boolean initialize(MedWellScannerServiceCallback callback, ArrayList<String> devMACS) {
		mDeviceMacs = devMACS;
        Log.i(TAG, "initialize: devMACS: " + devMACS.size());
		mDeviceAdvertising = new ArrayList<>();
		mPeripherals = new ArrayList<>();
		mCallback = callback;

        // For API level 18 and above, get a reference to BluetoothAdapter through
        // BluetoothManager.
        if (mBluetoothManager == null) {
            mBluetoothManager = (BluetoothManager) getSystemService(Context.BLUETOOTH_SERVICE);
            if (mBluetoothManager == null) {
                Log.e(TAG, "Unable to initialize BluetoothManager.");
                return false;
            }
            mBluetoothAdapter = mBluetoothManager.getAdapter();
            if (mBluetoothAdapter == null) {
                Log.e(TAG, "Unable to obtain a BluetoothAdapter.");
                return false;
            }
            setLeScanner();
            return mBluetoothAdapter.isEnabled();
        }
        return false;
    }

    private void setLeScanner() {
        mBluetoothLeScanner = mBluetoothAdapter.getBluetoothLeScanner();
    }

    public String getDeviceSerialNumber(int index) {
    	if(index < mDeviceAdvertising.size()) {
    		return mDeviceAdvertising.get(index).advSerialNumber;
    	}
    	return null;
    }

    public String getDeviceName(int index) {
    	if(index < mPeripherals.size()) {
    		return mPeripherals.get(index).getName();
    	}
    	return null;
    }

    public String getDeviceAddress(int index) {
    	if(index < mPeripherals.size()) {
    		return mPeripherals.get(index).getAddress();
    	}
    	return null;
    }

    private Runnable runScanForDevicePeripherals = new Runnable() {
        @Override
        public void run() {
            _scanForDevicePeripherals();
        }
    };
    private void _scanForDevicePeripherals() {
        if (mBluetoothLeScanner == null)
            setLeScanner();
        if (mBluetoothLeScanner != null) {
            mDeviceAdvertising.clear();
            mPeripherals.clear();

            mBluetoothLeScanner.startScan(mScanCallback);
            isScanning = true;
            Log.i(TAG,"Starting Peripheral Discovery...");
        }
    }
    public void scanForDevicePeripherals() {
        mHandler.postDelayed(runScanForDevicePeripherals, 500);
	}

	private Runnable runScanForNewData = new Runnable() {
        @Override
        public void run() {
            _scanForNewData();
        }
    };
    private void _scanForNewData() {
        if (mBluetoothLeScanner == null)
            setLeScanner();
        if(mBluetoothLeScanner != null) {
            List<ScanFilter> filters = new ArrayList<>();
            for (String mac : mDeviceMacs) {
                ScanFilter scanFilter = new ScanFilter.Builder().setDeviceAddress(mac).build();
                filters.add(scanFilter);
                Log.i(TAG, "_scanForNewData: adding filter: " + mac);
            }
            ScanSettings.Builder scanSettings = new ScanSettings.Builder().setCallbackType(ScanSettings.CALLBACK_TYPE_ALL_MATCHES);
            if (Build.VERSION.SDK_INT >= 23) {
                scanSettings.setMatchMode(ScanSettings.MATCH_MODE_AGGRESSIVE);
                scanSettings.setScanMode(ScanSettings.SCAN_MODE_BALANCED);
                scanSettings.setNumOfMatches(ScanSettings.MATCH_NUM_ONE_ADVERTISEMENT);
            }
            mBluetoothLeScanner.startScan(filters, scanSettings.build(), mScanCallback_newData);
            nextNDLogTime = System.currentTimeMillis() + 30000;
            isScanning = true;
            Log.i(TAG,"Starting New Data Discovery...");
        }
    }
    public void scanForNewData() {
        mHandler.postDelayed(runScanForNewData, 500);
    }

    public void stopScan() {
        isScanning = false;
		if(mBluetoothLeScanner != null) {
			mBluetoothLeScanner.stopScan(mScanCallback);

		}
	}

    public void stopNewDataScan() {
        isScanning = false;
        if(mBluetoothLeScanner != null) {
            mBluetoothLeScanner.stopScan(mScanCallback_newData);
            Log.i(TAG, "stopNewDataScan: advertisements without new data = " + numberOfNoDataHits);
            numberOfNoDataHits = 0;
        }
    }

    public boolean isNotScanning() {
        return !isScanning;
    }

    private final ScanCallback mScanCallback = new ScanCallback() {
        @Override
        public void onScanResult(int callbackType, ScanResult result) {
            super.onScanResult(callbackType, result);
            synchronized (oLock) {
                if (!isScanning  || hasScanResults) // suppress late callbacks.
                    return;
                hasScanResults = true;
                final BluetoothDevice device = result.getDevice();
                //if (mDeviceMacs.contains(device.getAddress())) // ignore devices already paired with.
                //    return; // now we will unconditionally re-pair devices...
                String devName = "";
                if (result.getScanRecord().getDeviceName() != null)
                    devName = result.getScanRecord().getDeviceName();
                if (devName.equals("MedWell")) {
                    Log.i(TAG, "Device name match");

                    if(!mPeripherals.contains(device)) {
                        mPeripherals.add(device);

                        com.healthsaas.zewamedwell.zewa.ble.BLEAdvertisingData advData = new com.healthsaas.zewamedwell.zewa.ble.BLEAdvertisingData();
                        advData.advRSSI = result.getRssi();
                        advData.advName = devName;
                        advData.advSerialNumber = serialNumberFromMAC(device.getAddress());
                        advData.flags = Objects.requireNonNull(result.getScanRecord()).getAdvertiseFlags();

                        Log.i(TAG, "New Device discovered " + advData.advName);
                        Log.i(TAG, "Advertising RSSI: " + advData.advRSSI);
                        Log.i(TAG, "Advertising Serial Number: " + advData.advSerialNumber);

                        mDeviceAdvertising.add(advData);

                        if (advData.isGeneral()) { // only surface devices with general discoverability set
                            stopScan();
                            int index = mPeripherals.indexOf(device);
                            mCallback.didDiscoverPeripheral(index);
                            mDeviceMacs.add(device.getAddress());
                        }
                    }
                    else {
                        Log.i(TAG, "Device rediscovered " + result.getScanRecord().getDeviceName());

                        int index = mPeripherals.indexOf(device);

                        com.healthsaas.zewamedwell.zewa.ble.BLEAdvertisingData advData = new BLEAdvertisingData();
                        advData.advRSSI = result.getRssi();
                        advData.advName = result.getScanRecord().getDeviceName();
                        advData.advSerialNumber = serialNumberFromMAC(device.getAddress());
                        advData.flags = Objects.requireNonNull(result.getScanRecord()).getAdvertiseFlags();

                        Log.i(TAG, "Advertising RSSI: " + advData.advRSSI);
                        Log.i(TAG, "Advertising Serial Number: " + advData.advSerialNumber);

                        mDeviceAdvertising.set(index, advData);

                        if (advData.isGeneral()) { // only surface devices with general discoverability set
                            stopScan();
                            mCallback.didDiscoverPeripheral(index);
                            mDeviceMacs.add(device.getAddress());
                        }
                    }
                }
                hasScanResults = false;
            }
        }

        @Override
        public void onScanFailed(int errorCode) {
            super.onScanFailed(errorCode);
            if (errorCode != 1)
                isScanning = false;
            Log.e(TAG, "mScanCallback.onScanFailed: " + String.valueOf(errorCode));
            mCallback.onDiscoverPeripheralError(errorCode);
        }
    };

    private final ScanCallback mScanCallback_newData = new ScanCallback() {
        @Override
        public void onScanResult(int callbackType, ScanResult result) {
            synchronized (oLock) { // scan is filtered by known MAC addresses - no need to filter here...
                if (!isScanning || hasScanResults) // suppress late callbacks.
                    return;
                hasScanResults = true;
                final BluetoothDevice device = result.getDevice();
                BLEAdvertisingData advData = new BLEAdvertisingData();
                advData.advSerialNumber = serialNumberFromMAC(result.getDevice().getAddress());
                advData.flags = Objects.requireNonNull(result.getScanRecord()).getAdvertiseFlags(); // known devices always have Advertising flags.

                if (advData.isGeneral() && !advData.isLimited()) { // only surface devices with general discoverability set
                    Log.i(TAG, "onScanResult: Device advertising with new data: S/N: " +
                            advData.advSerialNumber + " RSSI: " + String.valueOf(result.getRssi()));
                    stopNewDataScan();
                    mCallback.didDiscoverNewData(device.getAddress());
                } else {
                    numberOfNoDataHits++;
                    if (System.currentTimeMillis() > nextNDLogTime) {
                        nextNDLogTime = System.currentTimeMillis() + 30000;
                        Log.i(TAG, "onScanResult: advertising without new data hits in last 30s = " +
                                String.valueOf(numberOfNoDataHits));
                        numberOfNoDataHits = 0;
                    }
                }
                hasScanResults = false;
            }
            super.onScanResult(callbackType, result);
        }

        @Override
        public void onScanFailed(int errorCode) {
            super.onScanFailed(errorCode);
            if (errorCode != 1)
                isScanning = false;
            Log.e(TAG, "mScanCallback_newData.onScanFailed: " + String.valueOf(errorCode));
            mCallback.onDiscoverNewDataError(errorCode);
        }
    };

/*
    protected String byteToHexString(byte [] x) {
    	StringBuilder y = new StringBuilder();
    	
    	for(byte c : x) {
    	    y.append(String.format("%02X ", c));
    	}
    	return y.toString();
    }
*/

    private String serialNumberFromMAC(String mac) {
        StringBuilder resp = new StringBuilder();
        String[] parts = mac.split(":");
        for (String s : parts) {
            resp.insert(0, s);
        }
        return  resp.toString();
    }
    
}
