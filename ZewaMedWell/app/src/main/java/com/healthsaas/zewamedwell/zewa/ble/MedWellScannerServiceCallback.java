package com.healthsaas.zewamedwell.zewa.ble;

public interface MedWellScannerServiceCallback {
	void didDiscoverPeripheral(int deviceIndex);
	void onDiscoverPeripheralError(int err);
	void didDiscoverNewData(String macAddress);
	void onDiscoverNewDataError(int err);

}
