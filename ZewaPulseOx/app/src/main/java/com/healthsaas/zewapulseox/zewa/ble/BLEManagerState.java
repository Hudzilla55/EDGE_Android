package com.healthsaas.zewapulseox.zewa.ble;

public enum BLEManagerState {
    BLE_STANDBY,
    BLE_DISCOVER_PERIPHERAL,
    BLE_REQUEST_CONNECTION,
    BLE_CONNECTED,
    BLE_DISCOVER_SERVICES,
    BLE_DISCOVER_CHARACTERISTICS,
    BLE_INITIALIZE 
}
