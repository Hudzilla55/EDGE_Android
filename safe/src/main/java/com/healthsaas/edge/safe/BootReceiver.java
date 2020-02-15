package com.healthsaas.edge.safe;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.util.Log;

import java.util.Objects;

public class BootReceiver extends BroadcastReceiver {
    public BootReceiver() {
    }

    public void onReceive(Context context, Intent intent) {
        Log.i(this.getClass().getName(), "SafeSDK boot Receiver has started!");
        String action = intent.getAction();
        Uri data = intent.getData();

        if (Intent.ACTION_BOOT_COMPLETED.equalsIgnoreCase(action)) {
            // after each boot check for new settings for MedWell
            AppSettings.checkForNewSettings();

        } else if (Intent.ACTION_PACKAGE_REPLACED.equalsIgnoreCase(action) ) {
            if (context.getPackageName().equals(Objects.requireNonNull(data).getSchemeSpecificPart())) {
                Log.i(this.getClass().getSimpleName(), "onReceive: my package replaced: " + data.getSchemeSpecificPart());
            } else {
                Log.i(this.getClass().getSimpleName(), "onReceive: * package replaced: " + data.getSchemeSpecificPart());
            }
            // after any package upgrade check for new settings for MedWell
            AppSettings.checkForNewSettings();
        }
    }
}
