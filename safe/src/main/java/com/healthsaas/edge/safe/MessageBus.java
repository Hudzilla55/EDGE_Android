package com.healthsaas.edge.safe;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;

public class MessageBus extends BroadcastReceiver {
    public  MessageBus() {}

    @Override
    public void onReceive(Context context, Intent intent) {

        String action = intent.getAction();
        Bundle data = intent.getExtras();

        switch (action)
        {
            case "pairing":
                data.getString("Status", "none");
                data.getString("Model", "none");
                SafeSDK.getInstance().setStatus("");
        }

    }
}
