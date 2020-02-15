package com.healthsaas.zewamedwell;

import android.content.Context;
import android.content.Intent;
import android.util.Log;

//import com.appspector.sdk.AppSpector;

import com.healthsaas.edge.safe.SafeApp;


/*---------------------------------------------------------
 *
 * HealthSaaS, Inc
 *
 * Copyright 2018
 *
 * This file may not be copied, modified, or duplicated
 * without written permission from HealthSaaS, Inc.
 * in the terms outlined in the license provide to the
 * end user/users of this file.
 *
 ---------------------------------------------------------*/

public class ZewaMedWellApp extends SafeApp {
    private static ZewaMedWellApp mApp;

    public static Context getAppContext() {
        return mApp.getApplicationContext();
    }

    @Override
    public void onCreate() {
        super.onCreate();
        Log.i(TAG, "ZewaMedWellApp Application has started!");
        mApp = this;
        Thread.setDefaultUncaughtExceptionHandler(new ZewaMedWellExceptionHandler());

        // You can start all monitors
        //AppSpector.build(this).withDefaultMonitors().run("android_NjE5NzJhYjYtYjhmZC00Yzg1LWJiODEtNjE5NDk3NTNkOGUy");

        // start the main manager service.
        final Intent sIntent = new Intent(this, ZewaMedWellManager.class);
        sIntent.setAction(ZewaMedWellManager.ACTION_START_MEDWELL_MGR);
        startService(sIntent);
    }
}
