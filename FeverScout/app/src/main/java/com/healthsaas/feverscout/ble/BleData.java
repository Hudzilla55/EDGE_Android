package com.healthsaas.feverscout.ble;

import com.vivalnk.vdireader.VDIType;

import java.io.Serializable;

public class BleData implements Serializable{
	
	private static final long serialVersionUID = -1151382250238024534L;
	
	private String mDeviceId;
	private int mBatteryPercent;
	private float mTemperature;
	private float mfinalTemperature;
	private int mRssi;
	private long mReadingTime;
	private boolean mDummy = false;

	private VDIType.VDI_TEMPERATURE_STATUS mTemperatureStatus;


	
	public void setDeviceId(String deviceId) {
		mDeviceId = deviceId;
	}
	
	public String getDeviceId() {
		return mDeviceId;
	}

	public void setBatteryPercent(int batteryPercent) {
		mBatteryPercent = batteryPercent;
	}
	
	public int getBatteryPercent() {
		return mBatteryPercent;
	}
	
	public void setTemperatureValue(float temperature) {
		mTemperature = temperature;
	}
	
	public float getTemperatureValue() {
		return mTemperature;
	}

	public void setFinalTemperatureValue(float temperature) {
		mfinalTemperature = temperature;
	}

	public float getFinalTemperatureValue() {
		return mfinalTemperature;
	}

	public void setRSSI(int rssi) {
		mRssi = rssi;
	}

	public int getRSSI() {
		return mRssi;
	}

	public void setReadingTime(long readingTime) { mReadingTime = readingTime; }

	public long getReadingTime() { return mReadingTime; }

	public void setDummy(boolean isDummy) { mDummy = isDummy;}

	public boolean getDummy() { return mDummy;}

	public void setTemperatureStatus(VDIType.VDI_TEMPERATURE_STATUS status) {
		mTemperatureStatus = status;
	}

	public VDIType.VDI_TEMPERATURE_STATUS getTemperatureStatus() {
		return mTemperatureStatus;
	}
}
