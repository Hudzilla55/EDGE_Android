package com.healthsaas.zewals;

import android.content.Context;
import android.content.Intent;
import android.util.Log;

//import com.appspector.sdk.AppSpector;

import com.healthsaas.edge.safe.SafeApp;


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

public class ZewaLSApp extends SafeApp {
    private static ZewaLSApp mApp;

    public static Context getAppContext() {
        return mApp.getApplicationContext();
    }

    @Override
    public void onCreate() {
        super.onCreate();
        mApp = this;
        Log.i(TAG, "ZewaLS Application has started!");
        Thread.setDefaultUncaughtExceptionHandler(new ZewaLSExceptionHandler());

        // We recommend to start AppSpector from Application#onCreate method

        // You can start all monitors
        //AppSpector.build(this).withDefaultMonitors().run("android_YmQwZDJkYTctNWNmZC00YjBiLWIyODYtNDFhZTYyNWI3MWE4");


        // start the main manager service.
        final Intent sIntent = new Intent(this, ZewaLSManager.class);
        sIntent.setAction(ZewaLSManager.ACTION_START_ZEWA_MGR);
        startService(sIntent);
    }
}
