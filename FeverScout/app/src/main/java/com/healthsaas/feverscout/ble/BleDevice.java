package com.healthsaas.feverscout.ble;

import java.io.Serializable;

public class BleDevice implements Serializable{
	
	private static final long serialVersionUID = -5404305906224876702L;

	private String mDeviceId;
	private String mFwVersion;
	private int mRSSI;
	private String mAddress;

	public String getDeviceId() {
		return mDeviceId;
	}

	public void setDeviceId(String deviceId) {
		mDeviceId = deviceId;
	}

	public String getFwVersionInfo() {
		return mFwVersion;
	}

	public void setFwVersionInfo(String info) {
		mFwVersion = info;
	}

	public int getRSSI() { return mRSSI; }

	public void setRSSI(int RSSI) { mRSSI = RSSI; }

	public String getAddress() { return mAddress; }

	public void  setAddress(String Address) { mAddress = Address; }
}
