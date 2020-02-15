package com.healthsaas.zewapulseox;

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

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.os.SystemClock;
import android.util.Log;

public class ZewaPulseOxExceptionHandler implements Thread.UncaughtExceptionHandler {
    private static final String TAG = ZewaPulseOxExceptionHandler.class.getSimpleName();
    private static final int RESTART_DELAY_MS = 5000;

    @Override
    public void uncaughtException(Thread t, Throwable e) {
        Log.d(TAG, "Exception in thread " + t.toString());
        e.printStackTrace();

        recycleApp(-2);
    }

    static void recycleApp(int status) {
        recycleApp(status, RESTART_DELAY_MS);
    }

    static void recycleApp(int status, int delayMs) {
        Context context = ZewaPulseOxApp.getAppContext();
        // schedule a restart
        Intent intent = new Intent(context, MainActivity.class);
        intent.setAction(Intent.ACTION_DEFAULT);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK |
                Intent.FLAG_ACTIVITY_CLEAR_TASK |
                Intent.FLAG_ACTIVITY_CLEAR_TOP);
        PendingIntent pendingIntent = PendingIntent.getActivity(context,0, intent, PendingIntent.FLAG_ONE_SHOT);

        AlarmManager am = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        if (am != null) {
            am.set(AlarmManager.RTC_WAKEUP,System.currentTimeMillis() + delayMs, pendingIntent);
        }
        exitApp(status);
    }

    static void exitApp(int status) {
        // start the main manager service.
        final Intent sIntent = new Intent(ZewaPulseOxApp.getAppContext(), ZewaPulseOxManager.class);
        sIntent.setAction(ZewaPulseOxManager.ACTION_STOP_PULSEOX_MGR);
        ZewaPulseOxApp.getAppContext().stopService(sIntent);

        // pause for a second...
        SystemClock.sleep(Constants.STATE_TRANSITION_TIME * 5);
        // exit gracefully
        System.exit(status);
    }
}
