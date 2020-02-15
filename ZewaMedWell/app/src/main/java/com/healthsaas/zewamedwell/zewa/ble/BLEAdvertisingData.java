package com.healthsaas.zewamedwell.zewa.ble;

public class BLEAdvertisingData {
    public String advName;
    public String advSerialNumber;
    public double advRSSI;
    public int flags;
    public int advCount;

    public boolean isGeneral() {
        return (flags & 2) > 0;
    }
    public boolean isLimited() {
        return (flags & 1) > 0;
    }

}
