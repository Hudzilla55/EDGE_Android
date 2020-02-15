package com.healthsaas.feverscout.ble;


import android.content.Context;
import android.os.Build;
import android.os.Handler;

import com.vivalnk.vdireader.VDICommonBleListener;
import com.vivalnk.vdireader.VDICommonBleReader;
import com.vivalnk.vdireader.VDIType;
import com.vivalnk.vdireader.VDIType.CHARGER_BATTERY_STATUS;
import com.vivalnk.vdireaderimpl.VDIBleThermometer;
import com.vivalnk.vdireaderimpl.VDIBleThermometerL;
import com.vivalnk.vdiutility.viLog;

import java.lang.ref.WeakReference;




public class BleManager implements VDICommonBleListener {


	private static final String TAG = "BluetoothManager";

	private static volatile BleManager mInstance = null;

	private VDICommonBleReader mBleReader = null;
	private Context mContext;
	
   
	private WeakReference<Handler> mHandlerRef;
	

	public void destroy() {
		if (mHandlerRef != null)
			mHandlerRef.clear();
		if (mBleReader != null)
			mBleReader.destroy();
		
		
		mHandlerRef = null;
		mBleReader = null;
		mInstance = null;
	}
		

	public static BleManager getInstance(Context context) {
		if (mInstance == null) {
			synchronized (BleManager.class) {
				if (mInstance == null) {
					mInstance = new BleManager(context);
				}
			}
		}

		return mInstance;
	}

	private BleManager(Context context) {
		mContext = context;
		if (Build.VERSION.SDK_INT >= 21)
		    mBleReader = new VDIBleThermometerL(mContext);
		else 
			 mBleReader = new VDIBleThermometer(mContext);
		mBleReader.setListener(this);
		
	}

	public VDICommonBleReader getBleReader() {
		return mBleReader;
	}


	
	public void setHandler(Handler handler) {
		if (mHandlerRef == null) {
			mHandlerRef = new WeakReference<Handler>(handler);
		} else {
			mHandlerRef.clear();
			mHandlerRef = null;
			mHandlerRef = new WeakReference<Handler>(handler);
		}
	}

	@Override
	public void onNewDeviceDiscovered(String deviceId, String fwVersionInfo, String address, int rssi) {
		// TODO Auto-generated method stub
		if (mHandlerRef == null) {
			viLog.e(TAG, "mHandlerRef is null.");
			return;
		}

		Handler handler = mHandlerRef.get();
		if (handler == null) {
			viLog.e(TAG, "handler is null.");
			return;
		}
		
		BleDevice bleDevice = new BleDevice();
		bleDevice.setDeviceId(deviceId);
		bleDevice.setFwVersionInfo(fwVersionInfo);
		bleDevice.setAddress(address);
		bleDevice.setRSSI(rssi);
		
		handler.obtainMessage(BleConsts.MESSAGE_DEVICE_FOUND, bleDevice)
		.sendToTarget();
	}

	@Override
	public void onTemperatureUpdated(String deviceId, float rawTemperature, float finalTemperature,
									 int batteryPercent, int rssi, VDIType.VDI_TEMPERATURE_STATUS status) {

		if (mHandlerRef == null) {
			viLog.e(TAG, "mHandlerRef is null.");
			return;
		}

		Handler handler = mHandlerRef.get();
		if (handler == null) {
			viLog.e(TAG, "handler is null.");
			return;
		}
		
		BleData bleData = new BleData();
		bleData.setDeviceId(deviceId);
		bleData.setBatteryPercent(batteryPercent);
		bleData.setRSSI(rssi);
		bleData.setTemperatureValue(rawTemperature);
		bleData.setFinalTemperatureValue(finalTemperature);
		bleData.setTemperatureStatus(status);
		handler.obtainMessage(BleConsts.MESSAGE_TEMPERATURE_UPDATE, bleData)
		.sendToTarget();
	}

	@Override
	public void onChargerInfoUpdate(String deviceId, String chargerFW, CHARGER_BATTERY_STATUS batteryStatus) {
		if (mHandlerRef == null) {
			viLog.e(TAG, "mHandlerRef is null.");
			return;
		}

		Handler handler = mHandlerRef.get();
		if (handler == null) {
			viLog.e(TAG, "handler is null.");
			return;
		}

		StringBuilder result = new StringBuilder(deviceId);
		result.append(" charger info updated, chargerFW ").append(chargerFW);
		if (batteryStatus == CHARGER_BATTERY_STATUS.NORMAL) {
			result.append(", battery normal");
		}else {
			result.append(", battery low");
		}

		handler.obtainMessage(BleConsts.MESSAGE_CHARGER_INFO_UPDATED, result.toString())
				.sendToTarget();
	}

	@Override
	public void onDeviceLost(String deviceId) {

		if (mHandlerRef == null) {
			viLog.e(TAG, "mHandlerRef is null.");
			return;
		}

		Handler handler = mHandlerRef.get();
		if (handler == null) {
			viLog.e(TAG, "handler is null.");
			return;
		}
		
		handler.obtainMessage(BleConsts.MESSAGE_DEVIE_LOST, deviceId)
		.sendToTarget();
	}

	public void onTemperatureAbnormalStatusUpdate(String deviceId, VDIType.ABNORMAL_TEMPERATURE_STATUS status) {
		if (mHandlerRef == null) {
			viLog.e(TAG, "mHandlerRef is null.");
			return;
		}

		Handler handler = mHandlerRef.get();
		if (handler == null) {
			viLog.e(TAG, "handler is null.");
			return;
		}
		String notification = "" ;
		if (status == VDIType.ABNORMAL_TEMPERATURE_STATUS.ABNORMAL_LOW_TEMPERATURE) {
			notification = deviceId + " low temperature notification,temperature lower than 34.5 Celsius!";
		}
		handler.obtainMessage(BleConsts.MESSAGE_TEMPERATURE_ABNORAML, notification)
				.sendToTarget();
	}



	@Override
	public void onTemperatureMissed(String deviceId) {
		if (mHandlerRef == null) {
			viLog.e(TAG, "mHandlerRef is null.");
			return;
		}

		Handler handler = mHandlerRef.get();
		if (handler == null) {
			viLog.e(TAG, "handler is null.");
			return;
		}

		handler.obtainMessage(BleConsts.MESSAGE_TEMPERATURE_MISSED, deviceId)
				.sendToTarget();
	}

	public void phoneBluetoothOff() {
		if (mHandlerRef == null) {
			viLog.e(TAG, "mHandlerRef is null.");
			return;
		}

		Handler handler = mHandlerRef.get();
		if (handler == null) {
			viLog.e(TAG, "handler is null.");
			return;
		}

		handler.obtainMessage(BleConsts.MESSAGE_PHONE_BLUETOOTH_OFF)
				.sendToTarget();
	}

	public void phoneLocationOff() {
		if (mHandlerRef == null) {
			viLog.e(TAG, "mHandlerRef is null.");
			return;
		}

		Handler handler = mHandlerRef.get();
		if (handler == null) {
			viLog.e(TAG, "handler is null.");
			return;
		}

		handler.obtainMessage(BleConsts.MESSAGE_PHONE_LOCATION_OFF)
				.sendToTarget();
	}




}
