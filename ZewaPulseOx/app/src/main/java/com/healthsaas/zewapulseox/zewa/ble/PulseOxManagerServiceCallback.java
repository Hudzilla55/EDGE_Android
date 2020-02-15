package com.healthsaas.zewapulseox.zewa.ble;

public interface PulseOxManagerServiceCallback {
	void didCompleteConnect(boolean success);
	void didDisconnect();
	void didStartConnect();
	void didConnect();
}
