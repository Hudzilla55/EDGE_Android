package com.healthsaas.feverscout;

import android.Manifest;
import android.app.AlertDialog;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Message;
import android.telephony.TelephonyManager;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;

import java.lang.ref.WeakReference;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.Timer;
import java.util.TimerTask;

import androidx.annotation.Nullable;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.FragmentActivity;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.healthsaas.edge.safe.SafeSDK;
import com.healthsaas.feverscout.ble.BleConsts;
import com.healthsaas.feverscout.ble.BleData;
import com.healthsaas.feverscout.ble.BleDevice;
import com.healthsaas.feverscout.ble.BleManager;
import com.healthsaas.feverscout.dataModels.deviceObject;
import com.healthsaas.feverscout.dataModels.payloadObject;
import com.healthsaas.feverscout.vivalinkdb.BtDeviceDb;
import com.vivalnk.vdireader.VDIType;
import com.vivalnk.vdireaderimpl.VDIBleService;
import com.vivalnk.vdiutility.viLog;

import static com.healthsaas.feverscout.FeverScoutSettings.checkForNewSettings;


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

public class MainActivity extends FragmentActivity {
    private static final String TAG = MainActivity.class.getSimpleName();
    public static final String RECEIVE_MSG = "com.healthsaas.feverscout.RECEIVE_MSG";
    final static int PERMISSION_ACCESS_FINE_LOCATION = 0;
    final static int PERMISSION_ACCESS_PHONE_STATE = 1;
    final static int PERMISSION_ACCESS_FILESYSTEM = 2;
    final static int SCAN_PERIOD = 35 * 1000;

    private static final int MESSAGE_FLAG_SCAN_BLUETOOTH_TIMEOUT = 1;

    public static final int RESULT_CODE_OK = 100;
    public static final int REQUEST_CODE_OPEN_SCAN_DEVICE_UI = 10;
    public static final int REQUEST_PERMISSION = 101;

    private static final int MESSAGE_SCREEN_ON = 5;
    private static final int MESSAGE_SCREEN_OFF = 6;
    private static final int MESSAGE_UPDATE_UI = 8;

    private boolean isBackground = false;
    private BleManager mBleManager;
    private int mPairingRssi = -75;
    private int updates = 0;
    private long lastUpdateTime = 0;

    private  boolean isTemperatureUpdate = false;

    private LoadDataHandler mLoadDataHandler;

    private Timer mCheckDataReceiveTimer;
    private TimerTask mCheckDataReceiveTimerTask;
    private static final long DATARECEIVECHECKDELAY = 68000;
    private static final long DATARECEIVECHECKPERIOD = 64000;
    private int mForegroundReceiveCounts = 0;
    private int mBackgroundReceiveCounts = 0;
    private long mLatestTemperatureUpdateTime;
    private int mForegroundLostSamples = 0;
    private int mBackGroundLostSamples = 0;
    private int mForeGroundSamples = 0;
    private int mBackGroundSamples = 0;

    private long mBackgroundStartTime;

    private int pairedDevices = 0;
    private BtDeviceDb mBtDb;
    private ArrayList<deviceObject> mDevices;

    SharedPreferences sharedPrefs;
    LocalBroadcastManager bManager;
    TextView tvConnections;
    TextView tvConnectionInfo;
    TextView tvLastConnectTime;
    private boolean mIsScanning = false;
    private Handler mHandler = new Handler();
    private SafeSDK mSafeSDK = SafeSDK.getInstance();
    private String safeStatus = "Ready";
    private CharSequence[] serialNumbers;
    private boolean setupMode = false;
    private boolean isBooting = false;
    private boolean isStandalone = false;
    private boolean isStartTempUpdate = false;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.main_activity);

        TextView buildVer = findViewById(R.id.tv_version);
        buildVer.setText(String.format(Locale.US, "Version: %s", BuildConfig.VERSION_NAME));

        TextView buildTimestamp = findViewById(R.id.buildTimestamp);
        buildTimestamp.setText(String.format(Locale.US, "Built: %s", Utils.getISO8601(BuildConfig.TIMESTAMP)));

        tvConnections = findViewById(R.id.connections);
        tvConnectionInfo = findViewById(R.id.connection_info);
        tvLastConnectTime = findViewById(R.id.lastConnectTime);

        mBleManager = BleManager.getInstance(this.getApplicationContext());
        mLoadDataHandler = new LoadDataHandler(this);
        mBleManager.setHandler(mLoadDataHandler);
        VDI_init();

        getIMEI();
        Log.i(TAG, "Main Activity Launched. RootUrl: " + FeverScoutSettings.getRootURL());

        sharedPrefs = this.getApplicationContext().getSharedPreferences(Constants.PREFS_NAME, MODE_PRIVATE);
        setupMode = sharedPrefs.getBoolean("isFirstRun", true);
        if (setupMode)
            sharedPrefs.edit().putBoolean("isFirstRun", false).apply();
        bManager = LocalBroadcastManager.getInstance(this);
        IntentFilter intentFilter = new IntentFilter();
        intentFilter.addAction(RECEIVE_MSG);
        bManager.registerReceiver(bReceiver, intentFilter);

        mBtDb = new BtDeviceDb(FeverScoutApp.getAppContext());
        mBtDb.open();
        mDevices = new ArrayList<>();
        mBtDb.getAllDevices(mDevices);
        pairedDevices = mDevices.size();
        for (deviceObject d: mDevices) {
            mBleManager.getBleReader().addPDList(d.serialNumber);
        }
        updates = sharedPrefs.getInt("TotalConnections", 0);
        lastUpdateTime = sharedPrefs.getLong("LastConnectTime", 0);

        updateDisplay();
        if (mBleManager.getBleReader().getPDListLength() > 0) {
            isStartTempUpdate = true;
            checkScanningPermissions();
        }
    }

    @Override
    protected void onStart() {
        super.onStart();
    }

    @Override
    protected void onStop() {
        super.onStop();
    }

    @Override
    protected void onPause() {
        super.onPause();
        mHandler.removeCallbacks(homeTimeout);
        mHandler.removeCallbacks(pollStatus);
        unregisterReceiver(mReceiver);
    }

    @Override
    protected void onResume() {
        super.onResume();
        registerScreenActionReceiver();
        Bundle b = getIntent().getExtras();
        if(b != null) {
            setupMode = b.getBoolean("setupMode");
            isBooting = b.getBoolean("isBooting");
        }
        mHandler.postDelayed(homeTimeout, 130000 + (setupMode ? 480000 : 0));
        isStandalone = getPackageManager().getLaunchIntentForPackage("net.healthsaas.edge") == null;
        if (setupMode || isStandalone)
            invalidateOptionsMenu();
        mHandler.postDelayed(pollStatus, 250);
        if (!isStandalone && isBooting)
            goHome();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        bManager.unregisterReceiver(bReceiver);
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.menu_main, menu);
        if (mIsScanning) {
            menu.findItem(R.id.action_settings_scan).setTitle(R.string.action_settings_scanning);
            menu.findItem(R.id.action_settings_scan).setEnabled(false);
        }
        if (pairedDevices == 0) {
            menu.findItem(R.id.action_settings_remove).setEnabled(false);
        }
        if (setupMode || isStandalone) {
            menu.findItem(R.id.action_settings_remove).setVisible(true);
            menu.findItem(R.id.action_settings_scan).setVisible(true);
        }
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.
        int id = item.getItemId();

        if (id == R.id.action_settings_scan) {
            if (!mIsScanning) {
                startScanning();
                invalidateOptionsMenu();
            }
            return true;
        }
        else  if (id == R.id.action_settings_remove) {
            mDevices.clear();
            mBtDb.getAllDevices(mDevices);
            pairedDevices = mDevices.size();
            List<String> sList = new ArrayList<>();
            for (deviceObject dev: mDevices) {
                sList.add(dev.serialNumber.replace("/", "."));
            }
            serialNumbers = sList.toArray(new CharSequence[sList.size()]);
            showRemoveDialog();
            invalidateOptionsMenu();
            return true;
        }
        else  if (id == R.id.action_settings_restart) {
            FeverScoutExceptionHandler.recycleApp(-5);
            return true;
        }
        else if (id == R.id.action_home) {
            goHome();
        }

        return super.onOptionsItemSelected(item);
    }

    private void VDI_init() {

        if (!com.vivalnk.temptest.utils.GPSUtils.isGPSEnable(getApplicationContext())) {
            Toast.makeText(MainActivity.this, "location is not enabled! ", Toast.LENGTH_LONG).show();
        }
        VDIType.VDI_CHECKBLE_STATUS_TYPE checkResult = mBleManager.getBleReader().checkBle();
        if (checkResult == VDIType.VDI_CHECKBLE_STATUS_TYPE.SYSTEM_BLE_NOT_ENABLED) {
            Toast.makeText(MainActivity.this, "BLE is not enabled now, please enable BLE in the setting page", Toast.LENGTH_LONG).show();
        }else if (checkResult == VDIType.VDI_CHECKBLE_STATUS_TYPE.SYSTEM_LOCATION_NOT_ENABLED) {
            Toast.makeText(MainActivity.this, "You need to allow location update permission for this app to enable BLE scanning", Toast.LENGTH_LONG).show();
        }else if (checkResult == VDIType.VDI_CHECKBLE_STATUS_TYPE.SYSTEM_NOT_SUPPORT_BLE) {
            Toast.makeText(MainActivity.this, "BLE is not available", Toast.LENGTH_LONG).show();
        }

        //set zero to disable this feature
        mBleManager.getBleReader().setLostThreshold(0);
    }

    private void goHome() {
        Intent launchIntent = getPackageManager().getLaunchIntentForPackage("net.healthsaas.edge");
        if (launchIntent != null) {//null pointer check in case package name was not found
            launchIntent.addFlags(Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED | Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS);
            startActivity(launchIntent);
        }
    }

    private void updateDisplay() {
        int connections = sharedPrefs.getInt("TotalConnections", 0);
        long lastConnectTime = sharedPrefs.getLong("LastConnectTime",0);
        tvConnections.setText(String.format(Locale.US, "Updates: %d", connections));
        tvLastConnectTime.setText(String.format(Locale.US, "Last: %s", Utils.getISO8601(lastConnectTime)));
        tvConnectionInfo.setText(String.format(Locale.US, "Paired Devices: %d", pairedDevices));
    }

    private BroadcastReceiver bReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            _onReceive(intent);
        }

        private void _onReceive(Intent intent) {
            if(RECEIVE_MSG.equals(intent.getAction())) {
                updateDisplay();
            }
        }
    };

    private void registerScreenActionReceiver(){
        final IntentFilter filter = new IntentFilter();
        filter.addAction(Intent.ACTION_SCREEN_OFF);
        filter.addAction(Intent.ACTION_SCREEN_ON);

        registerReceiver(mReceiver, filter);
    }

    private final BroadcastReceiver mReceiver = new BroadcastReceiver(){
        @Override
        public void onReceive(final Context context, final Intent intent) {
            _onReceive(intent);
        }

        private void _onReceive(Intent intent) {
            // Do your action here
            if (Intent.ACTION_SCREEN_OFF.equals(intent.getAction())) {
                isBackground = true;
                mLoadDataHandler.sendEmptyMessage(MESSAGE_SCREEN_OFF);
            } else if (Intent.ACTION_SCREEN_ON.equals(intent.getAction())) {
                isBackground = false;
                mLoadDataHandler.sendEmptyMessage(MESSAGE_SCREEN_ON);
            }
        }
    };

    @Override
    public void onRequestPermissionsResult(int requestCode,
                                           String permissions[], int[] grantResults) {
        switch (requestCode) {
            case PERMISSION_ACCESS_FINE_LOCATION:
                // If request is cancelled, the result arrays are empty.
                if (grantResults.length > 0
                        && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    // permission was granted, yay! Do the
                    // ble scanning task.
                    // Restart the app after setting a flag that will cause it to go into scanning mode...
                    mBleManager.getBleReader().checkBle();
                    _startScanning();
                }
                break;

            case PERMISSION_ACCESS_PHONE_STATE:
                if (grantResults.length > 0
                        && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    // permission was granted, yay! Do the
                    // ble scanning task.
                    _getIMEI();
                    checkFileAccessPermissions();
                }
                break;

            case PERMISSION_ACCESS_FILESYSTEM:
                if (grantResults.length > 0
                        && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    checkForNewSettings();
                }
                break;
        }
    }

    protected void checkScanningPermissions() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)!= PackageManager.PERMISSION_GRANTED) {
            // Permission is not granted
            // Should we show an explanation?
            if (ActivityCompat.shouldShowRequestPermissionRationale(this,
                    Manifest.permission.ACCESS_FINE_LOCATION)) {
                // Show an explanation to the user *asynchronously* -- don't block
                // this thread waiting for the user's response! After the user
                // sees the explanation, try again to request the permission.
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        AlertDialog.Builder builder = new AlertDialog.Builder(
                                MainActivity.this)
                                .setTitle(R.string.permission_required)
                                .setCancelable(true)
                                .setPositiveButton("Ok",
                                        new DialogInterface.OnClickListener() {
                                            @Override
                                            public void onClick(
                                                    DialogInterface dialog,
                                                    int which) {
                                                askForScanningPermission();
                                            }

                                        }).setMessage(R.string.permission_reason_scan);

                        builder.create().show();
                    }
                });

            } else {
                // No explanation needed; request the permission
                askForScanningPermission();
            }
        } else {
            // Permission has already been granted
            _startScanning();
        }
    }

    protected void checkPhoneStatePermissions() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_PHONE_STATE)!= PackageManager.PERMISSION_GRANTED) {
            // Permission is not granted
            // Should we show an explanation?
            if (ActivityCompat.shouldShowRequestPermissionRationale(this,
                    Manifest.permission.READ_PHONE_STATE)) {
                // Show an explanation to the user *asynchronously* -- don't block
                // this thread waiting for the user's response! After the user
                // sees the explanation, try again to request the permission.
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        AlertDialog.Builder builder = new AlertDialog.Builder(
                                MainActivity.this)
                                .setTitle(R.string.permission_required)
                                .setCancelable(true)
                                .setPositiveButton("Ok",
                                        new DialogInterface.OnClickListener() {
                                            @Override
                                            public void onClick(
                                                    DialogInterface dialog,
                                                    int which) {
                                                askForPhoneStatePermission();
                                            }

                                        }).setMessage(R.string.permission_reason_state);

                        builder.create().show();
                    }
                });

            } else {
                // No explanation needed; request the permission
                askForPhoneStatePermission();
            }
        } else {
            // Permission has already been granted
            _getIMEI();
            checkFileAccessPermissions();
        }
    }

    protected void checkFileAccessPermissions() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE)!= PackageManager.PERMISSION_GRANTED) {
            // Permission is not granted
            // Should we show an explanation?
            if (ActivityCompat.shouldShowRequestPermissionRationale(this, Manifest.permission.WRITE_EXTERNAL_STORAGE)) {
                // Show an explanation to the user *asynchronously* -- don't block
                // this thread waiting for the user's response! After the user
                // sees the explanation, try again to request the permission.
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        AlertDialog.Builder builder = new AlertDialog.Builder(
                                MainActivity.this)
                                .setTitle(R.string.permission_required)
                                .setCancelable(true)
                                .setPositiveButton("Ok",
                                        new DialogInterface.OnClickListener() {
                                            @Override
                                            public void onClick(
                                                    DialogInterface dialog,
                                                    int which) {
                                                askForFileAccessPermission();
                                            }

                                        }).setMessage(R.string.permission_reason_read_files);

                        builder.create().show();
                    }
                });

            } else {
                // No explanation needed; request the permission
                askForFileAccessPermission();
            }
        } else {
            // Permission has already been granted
            checkForNewSettings();
        }
    }

    private void askForScanningPermission() {
        ActivityCompat.requestPermissions(MainActivity.this,
                new String[]{Manifest.permission.ACCESS_FINE_LOCATION,Manifest.permission.ACCESS_COARSE_LOCATION },
                PERMISSION_ACCESS_FINE_LOCATION);
    }

    private void askForPhoneStatePermission() {
        ActivityCompat.requestPermissions(MainActivity.this,
                new String[]{Manifest.permission.READ_PHONE_STATE },
                PERMISSION_ACCESS_PHONE_STATE);
    }

    private void askForFileAccessPermission() {
        ActivityCompat.requestPermissions(MainActivity.this,
                new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE, Manifest.permission.READ_EXTERNAL_STORAGE },
                PERMISSION_ACCESS_FILESYSTEM);
    }

    private void startScanning() {
        isStartTempUpdate = false;
        checkScanningPermissions();
    }

    private void getIMEI() {
        checkPhoneStatePermissions();
    }

    private void _startScanning() {
        if (isStartTempUpdate) {
            _startTempUpdate();
            return;
        }
        // signal manager service to stop scanning for new data..
        final Intent sIntent = new Intent(this, FeverScoutManager.class);
        sIntent.setAction(FeverScoutManager.ACTION_STOP_NEW_DATA_SCAN);
        startService(sIntent);

        mIsScanning = true;
        invalidateOptionsMenu();
        // look for new Fever Scout's...
        isTemperatureUpdate = false;
        mBleManager.getBleReader().setPairingRssi(mPairingRssi);
        mBleManager.setHandler(mLoadDataHandler);
        mBleManager.getBleReader().startDeviceDiscovery();
        mLoadDataHandler.removeMessages(MESSAGE_FLAG_SCAN_BLUETOOTH_TIMEOUT);
        mLoadDataHandler.sendEmptyMessageDelayed(MESSAGE_FLAG_SCAN_BLUETOOTH_TIMEOUT, SCAN_PERIOD);
        SafeSDK.getInstance().statusUpdate(Constants.BT_STATUS_SEARCHING, "", Constants.VIVA_MODEL_FEVERSCOUT);
    }

    private void _startTempUpdate() {
         // signal manager service to scan for new data..
        final Intent sIntent = new Intent(this, FeverScoutManager.class);
        sIntent.setAction(FeverScoutManager.ACTION_START_NEW_DATA_SCAN);
        startService(sIntent);
    }

    private void _getIMEI() {
        try {
            TelephonyManager telephonyManager = (TelephonyManager)getSystemService(Context.TELEPHONY_SERVICE);
            String IMEI = telephonyManager.getDeviceId();
            if (IMEI != null)
                mSafeSDK.setIMEI(IMEI);
            mSafeSDK.setSerialNumber(Build.SERIAL);
        } catch (SecurityException ex) {
            Log.d(TAG, "_getIMEI: " + ex.toString());
        }
    }

    private void stopScanning () {
        if (mIsScanning) {
            mIsScanning = false;
            invalidateOptionsMenu();
        }
    }

    private void updateSafeStatus() {
        TextView status = findViewById(R.id.statusText);
        status.setText(safeStatus);
    }

    private Runnable pollStatus = new Runnable() {
        @Override
        public void run() {
            String newStatus = mSafeSDK.getStatus();
            if (!newStatus.equals(safeStatus)) {
                if (newStatus.equals(Constants.BT_STATUS_PAIR_OK)) {
                    pairedDevices++;
                    updateDisplay();
                    invalidateOptionsMenu();
                } else if (newStatus.equals(Constants.BT_STATUS_UNPAIR_OK)) {
                    invalidateOptionsMenu();
                }
                safeStatus = newStatus;
                updateSafeStatus();
            }
            mHandler.postDelayed(pollStatus, 250);
        }
    };

    private void unPairFeverScout(String serialNumber) {
        if (serialNumber != null && !serialNumber.isEmpty()) {
            removeDevice(serialNumber);
            SafeSDK.getInstance().statusUpdate(Constants.BT_STATUS_UNPAIR_OK, serialNumber, Constants.VIVA_MODEL_FEVERSCOUT);
            Log.d(TAG, String.format("%s sn=%s removed", Constants.VIVA_MODEL_FEVERSCOUT, serialNumber));
        }
    }

    private Runnable homeTimeout = new Runnable() {
        @Override
        public void run() {
            goHome();
        }
    };

    public void showRemoveDialog() {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                AlertDialog.Builder builder = new AlertDialog.Builder(MainActivity.this);
                builder.setTitle(R.string.select_pulseox_to_remove)
                        .setNegativeButton(R.string.button_cancel, new DialogInterface.OnClickListener() {
                            public void onClick(DialogInterface dialog, int id) {
                                // User cancelled the dialog
                            }
                        })
                        .setItems(serialNumbers, new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialog, int which) {
                                final String RemoveSerialNumber = serialNumbers[which].toString();
                                AlertDialog.Builder builder2 = new AlertDialog.Builder(MainActivity.this);
                                builder2.setTitle(R.string.confirm_pulseox_to_remove)
                                        .setMessage("Remove this Fever Scout: " + RemoveSerialNumber)
                                        .setPositiveButton(R.string.button_remove, new DialogInterface.OnClickListener() {
                                            public void onClick(DialogInterface dialog, int id) {
                                                // Remove this MedWell!
                                                unPairFeverScout(RemoveSerialNumber.replace(".", "/"));
                                                pairedDevices--;
                                                updateDisplay();
                                            }
                                        })
                                        .setNegativeButton(R.string.button_cancel, new DialogInterface.OnClickListener() {
                                            public void onClick(DialogInterface dialog, int id) {
                                                // User cancelled the dialog
                                            }
                                        });
                                builder2.create().show();
                            }
                        });
                builder.create().show();
            }
        });
    }

    private static class LoadDataHandler extends Handler {
        private WeakReference<MainActivity> mTarget;

        public LoadDataHandler(MainActivity activity) {
            mTarget = new WeakReference<>(activity);
        }

        @Override
        public void handleMessage(Message msg) {
            super.handleMessage(msg);
            MainActivity activity = mTarget.get();

            if (activity == null || activity.isFinishing()) {
                return;
            }
            switch (msg.what) {
                case MESSAGE_SCREEN_ON:
                    viLog.d(TAG,"Message    :MESSAGE_SCREEN_ON()");
                    activity.isBackground = false;
                    if (activity.isTemperatureUpdate) {
                        if (activity.mBackgroundStartTime == 0) {
                            activity.mBackgroundStartTime = System.currentTimeMillis();
                            return;
                        }
                        long currentTimeM = System.currentTimeMillis();
                        long samples = (currentTimeM - activity.mBackgroundStartTime)/64000;
                        activity.mBackGroundSamples += samples;
                        if ((currentTimeM - activity.mLatestTemperatureUpdateTime ) > 68000) {
                            long lostSamples = (currentTimeM - activity.mLatestTemperatureUpdateTime)/64000;
                            activity.mBackGroundLostSamples += lostSamples;
                            activity.mLatestTemperatureUpdateTime = System.currentTimeMillis();
                        }
                    }
                    break;
                case MESSAGE_SCREEN_OFF:
                    viLog.d(TAG,"Message    :MESSAGE_SCREEN_OFF()");
                    activity.isBackground = true;
                    if (activity.isTemperatureUpdate) {
                        activity.mBackgroundStartTime = System.currentTimeMillis();
                    }
                    break;
                case MESSAGE_UPDATE_UI:
                    Log.i(TAG, "handleMessage: MESSAGE_UPDATE_UI");
                    activity.updateDisplay();
                    break;
                case BleConsts.MESSAGE_PHONE_BLUETOOTH_OFF:
                    Toast.makeText(activity, "phone bluetooth off", Toast.LENGTH_LONG).show();
                    break;
                case BleConsts.MESSAGE_PHONE_LOCATION_OFF:
                    Toast.makeText(activity, "phone location off", Toast.LENGTH_LONG).show();
                    break;
                case BleConsts.MESSAGE_DEVICE_FOUND:
                    activity.mLoadDataHandler.removeMessages(MESSAGE_FLAG_SCAN_BLUETOOTH_TIMEOUT);
                    BleDevice device = (BleDevice) msg.obj;
                    activity.mBleManager.getBleReader().stopDeviceDiscovery();
                    activity.stopScanning();

                    deviceObject discoveredDevice = new deviceObject(
                            Constants.VIVA_MODEL_FEVERSCOUT,
                            device.getDeviceId(),
                            device.getAddress(),
                            "", activity.mBleManager.getBleReader().getPDListLength());
                    activity.insertDevice(discoveredDevice);
                    SafeSDK.getInstance().statusUpdate(Constants.BT_STATUS_PAIR_OK, "", Constants.VIVA_MODEL_FEVERSCOUT);
                    BleData dummy = new BleData();
                    dummy.setReadingTime(System.currentTimeMillis());
                    dummy.setDeviceId(device.getDeviceId());
                    dummy.setBatteryPercent(0);
                    dummy.setRSSI(device.getRSSI());
                    dummy.setTemperatureValue(0);
                    dummy.setFinalTemperatureValue(0);
                    dummy.setTemperatureStatus(VDIType.VDI_TEMPERATURE_STATUS.TASTATUS_WARMUP);
                    dummy.setDummy(true);
                    activity.insertData(dummy);
                    if (activity.mBleManager.getBleReader().getPDListLength() > 0) {
                        activity._startTempUpdate();
                    }
                    break;
                case MESSAGE_FLAG_SCAN_BLUETOOTH_TIMEOUT:
                    activity.mBleManager.getBleReader().stopDeviceDiscovery();
                    if (activity.mIsScanning) {
                        SafeSDK.getInstance().statusUpdate(Constants.BT_STATUS_PAIR_NOT_FOUND, "", Constants.VIVA_MODEL_FEVERSCOUT);
                        activity.stopScanning();
                    }
                    if (activity.mBleManager.getBleReader().getPDListLength() > 0) {
                        activity._startTempUpdate();
                    }
                    break;
                default:
                    break;
            }
        }
    }

    static float castOneDecimalFloat(float f) {
        int decimal = 1;
        BigDecimal bd = new BigDecimal(f);
        bd = bd.setScale(decimal, RoundingMode.HALF_UP);
        return bd.floatValue();
    }

    synchronized void removeDevice(final String devId) {
        mBleManager.getBleReader().removePDList(devId);
        mBtDb.deleteBySN(devId);
    }

    synchronized void insertDevice(final deviceObject dev) {
        mBleManager.getBleReader().addPDList(dev.serialNumber);
        mBtDb.insert(dev);
    }

    synchronized void insertData(final BleData data) {
        if (data != null) {
            updates++;
            lastUpdateTime = System.currentTimeMillis();
            updateDisplay();
            Gson gson = new GsonBuilder().disableHtmlEscaping().create();

            String model = Constants.VIVA_MODEL_FEVERSCOUT;

            payloadObject po = new payloadObject();
            po.dt = "BT";

            po.hi.IMEI = SafeSDK.getInstance().getIMEI();
            po.hi.SN = SafeSDK.getInstance().getSerialNumber();

            //po.d.rt = Utils.fmtISO8601_receiptTime(); // always same as measure time...
            po.d.bl = String.valueOf(data.getBatteryPercent());
            po.d.man = Constants.VIVA_MFG_NAME;
            po.d.mod = model;
            po.d.sn = data.getDeviceId().replace("/", "."); // the box serial number has a period, not a slash...;
            po.d.mt = Utils.getISO8601(data.getReadingTime());
            po.d.u = "c";
            if (data.getDummy()) {
                po.d.bs = "N/A";
            } else {
                po.d.bs = "ALT/ArmPit";
            }
            float tempC = castOneDecimalFloat(data.getFinalTemperatureValue()); // final temp from fever scout
            float tempF = castOneDecimalFloat(((tempC * 9) / 5) + 32);
            po.d.to = String.valueOf(castOneDecimalFloat(tempF));
            po.d.tr = String.valueOf(castOneDecimalFloat(data.getTemperatureValue())); // raw temp from Fever Scout
            po.d.t = String.valueOf(tempC);
            po.d.rssi = String.valueOf(data.getRSSI());

            String x = gson.toJson(po);
            //Log.i(TAG, x);
            String endpointURL = String.format("%s%s", FeverScoutSettings.getRootURL(), "sppost.svc/ga");
            boolean bInserted = SafeSDK.getInstance().insertData(x, "Content-type:application/json;charset=UTF-8", endpointURL);
            if (bInserted) {
                Log.d("insertData", "Inserted BT");
            } else {
                Log.e("insertData", "Failed to insert BT");
            }
            SafeSDK.getInstance().drainSafe();
        }
    }

}
