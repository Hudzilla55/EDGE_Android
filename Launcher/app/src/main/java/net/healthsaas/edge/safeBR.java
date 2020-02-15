package net.healthsaas.edge;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.util.Log;

import com.healthsaas.edge.safe.SafeSDK;

public class safeBR extends BroadcastReceiver {
    private static final String TAG = safeBR.class.getSimpleName();

    @Override
    public void onReceive(Context context, Intent intent) {

        String action = intent.getAction();
        if (action == null)
            return;

        long delay = System.currentTimeMillis() - intent.getLongExtra("timestamp", System.currentTimeMillis());

        switch(action)
        {
            case "com.healthsaas.safe.STATUS_UPDATE":
                String status = intent.getStringExtra("STATUS");
                SafeSDK.getInstance().setStatus(status);
                Log.d(TAG, "Status '"+status+"' received, " + delay + "ms delay");
               break;
            case "com.healthsaas.safe.DEVICE_COUNT":
                Log.d(TAG, "Action '"+action+"' received, " + delay + "ms delay");
                break;
            default:
                Log.d(TAG, "Action '"+action+"' received, " + delay + "ms delay");
                break;
        }
    }

}
