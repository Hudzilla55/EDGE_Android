package com.healthsaas.feverscout;

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

public class FeverScoutApp extends SafeApp {
    private static FeverScoutApp mApp;

    public static Context getAppContext() {
        return mApp.getApplicationContext();
    }

    @Override
    public void onCreate() {
        super.onCreate();
        Log.i(TAG, "FeverScoutApp Application has started!");
        mApp = this;
        Thread.setDefaultUncaughtExceptionHandler(new FeverScoutExceptionHandler());

        // You can start all monitors
        //AppSpector.build(this).withDefaultMonitors().run("android_ODA0ZWU2ZTctMmU1MS00ZGFkLWIzMWUtOWI3ZWQ3MjA5Yzgz");

        // start the main manager service.
        final Intent sIntent = new Intent(this, FeverScoutManager.class);
        sIntent.setAction(FeverScoutManager.ACTION_START_FS_MGR);
        startService(sIntent);
    }
}
