package com.healthsaas.zewals;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.SystemClock;
import android.util.Log;

public class ZewaLSBTEventBR extends BroadcastReceiver {
    private static final String TAG = ZewaLSBTEventBR.class.getSimpleName();

    @Override
    public void onReceive(Context context, Intent intent) {

        String action = intent.getAction();
        if (action == null)
            return;

        Log.d(TAG, "Action "+action+" received");
        int state;

        switch(action)
        {
            case BluetoothAdapter.ACTION_STATE_CHANGED:
                state = intent.getIntExtra(BluetoothAdapter.EXTRA_STATE, -1);
                if (state == BluetoothAdapter.STATE_OFF)
                {
                    Log.d(TAG, "Bluetooth is off");
                    // wait 15s to turn BT back on...
                    SystemClock.sleep(15000);
                    checkBluetooth(context);
                    ZewaLSExceptionHandler.exitApp(0);
                }
                else if (state == BluetoothAdapter.STATE_TURNING_OFF)
                {
                    Log.d(TAG, "Bluetooth is turning off");
                    // recycle the App/Main service (which should stop the child service(s)...)
                    ZewaLSExceptionHandler.exitApp(0);
                }
                else if (state == BluetoothAdapter.STATE_TURNING_ON)
                {
                    Log.d(TAG, "Bluetooth is turning on");
                }
                else if(state == BluetoothAdapter.STATE_ON)
                {
                    Log.d(TAG, "Bluetooth is on");
                }
                else
                {
                    Log.d(TAG, "onReceive: unknown state: " + String.valueOf(state));
                }
                break;

        }
    }

    void checkBluetooth(Context context) {
        final BluetoothManager btMgr = (BluetoothManager) context.getSystemService(Context.BLUETOOTH_SERVICE);
        final BluetoothAdapter btAdapter;
        if (btMgr != null) {
            btAdapter = btMgr.getAdapter();
            if ( ! btAdapter.isEnabled() ) {
                Log.d(TAG, "Turning on BluetoothAdapter");
                btAdapter.enable();
            }
        }
    }
}
