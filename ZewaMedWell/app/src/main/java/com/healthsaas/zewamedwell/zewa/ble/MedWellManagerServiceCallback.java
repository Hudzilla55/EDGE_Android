package com.healthsaas.zewamedwell.zewa.ble;

public interface MedWellManagerServiceCallback {
	void didCompleteConnect(boolean success);
	void didDisconnect();
	void didReadClock(long ticks);
	void didWriteBlock(boolean success);
	void didCompleteRead(boolean success);
	void didUpdateReadProgress(double progress);
	void didStartConnect();
	void didConnect();
}
