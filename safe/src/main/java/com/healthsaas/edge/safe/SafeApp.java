package com.healthsaas.edge.safe;

import android.app.Application;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.Handler;
import android.os.IBinder;
import android.util.Log;


public class SafeApp extends Application {

	public static final String TAG = "SafeApp";
	public static SafeApp sAppContext;
	public static SafeService sSafeService;


	public ServiceConnection svcConn = new ServiceConnection() {
		public void onServiceConnected(ComponentName className, IBinder binder) {
			SafeApp.sSafeService = ((SafeService.LocalBinder) binder).getService();
			Log.i(TAG, "Service connected");
		}

		public void onServiceDisconnected(ComponentName className) {
			SafeApp.sSafeService = null;
			Log.i(TAG, "Service Not Connected");
			SafeApp.this.reConnectToSafe();
		}
	};

	@Override
	public void onCreate() {
		super.onCreate();

		sAppContext = this;

		Log.d(TAG, "SafeSDK Application has started!");

		Intent intent = new Intent(sAppContext.getApplicationContext(), SafeService.class);
		intent.setAction("connect");
		bindService(intent, svcConn, Context.BIND_AUTO_CREATE);

//		Intent schedulerIntent = new Intent(sAppContext, SchedulerService.class);
//		startService(schedulerIntent);

	}

	private void reConnectToSafe() {
		Handler handler = new Handler();
		handler.postDelayed(new Runnable() {
			public void run() {
				Log.i(TAG, "Came to reconnect Safe");
				Intent intent = new Intent(SafeApp.this.getApplicationContext(), SafeService.class);
				intent.setAction("reconnect");
				SafeApp.this.bindService(intent, SafeApp.this.svcConn, Context.BIND_AUTO_CREATE);
			}
		}, 1000L);
	}

}
