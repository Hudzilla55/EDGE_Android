package com.healthsaas.zewapulseox.zewa.ble;

public interface PulseOxScannerServiceCallback {
	void didDiscoverPeripheral(int deviceIndex);
	void onDiscoverPeripheralError(int err);
	void didDiscoverNewData(String macAddress);
	void onDiscoverNewDataError(int err);
//	public void didCompleteConnect(boolean success);
//	public void didDisconnect();
//	public void didCompleteRead(boolean success);
	
}
