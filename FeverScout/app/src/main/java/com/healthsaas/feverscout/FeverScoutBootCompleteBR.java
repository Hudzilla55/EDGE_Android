package com.healthsaas.feverscout;

import android.annotation.TargetApi;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.util.Log;

import java.util.Objects;

/*---------------------------------------------------------
 *
 * HealthSaaS, Inc.
 *
 * Copyright 2018
 *
 * This file may not be copied, modified, or duplicated
 * without written permission from HealthSaaS, Inc.
 * in the terms outlined in the license provide to the
 * end user/users of this file.
 *
 ---------------------------------------------------------*/

@TargetApi(23)
public class FeverScoutBootCompleteBR extends BroadcastReceiver {
    private static final String TAG = FeverScoutBootCompleteBR.class.getSimpleName();

    @Override
    public void onReceive(Context context, Intent intent) {
        String action = intent.getAction();
        Uri data = intent.getData();

        if (Intent.ACTION_BOOT_COMPLETED.equalsIgnoreCase(action)) {
            // after each boot check for new settings for PulseOx
            FeverScoutSettings.checkForNewSettings();

            Intent startIntent = new Intent(context.getApplicationContext(), MainActivity.class);
            startIntent.setAction(Intent.ACTION_DEFAULT);
            startIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK |
                    Intent.FLAG_ACTIVITY_CLEAR_TASK |
                    Intent.FLAG_ACTIVITY_CLEAR_TOP);
            Bundle b = new Bundle();
            b.putBoolean("setupMode", false);
            b.putBoolean("isBooting", true);
            startIntent.putExtras(b);
            context.startActivity(startIntent);
        } else if (Intent.ACTION_PACKAGE_REPLACED.equalsIgnoreCase(action) ) {
            if (context.getPackageName().equals(Objects.requireNonNull(data).getSchemeSpecificPart())) {
                Log.i(TAG, "onReceive: my package replaced: " + data.getSchemeSpecificPart());
                Intent startIntent = new Intent(context.getApplicationContext(), MainActivity.class)
                        .setAction(action).setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                Bundle b = new Bundle();
                b.putBoolean("setupMode", false);
                b.putBoolean("isBooting", true);
                startIntent.putExtras(b);
                context.startActivity(startIntent);
            } else {
                Log.i(TAG, "onReceive: * package replaced: " + data.getSchemeSpecificPart());
            }
            // after any package upgrade check for new settings for PulseOx
            FeverScoutSettings.checkForNewSettings();
        }
    }
}
