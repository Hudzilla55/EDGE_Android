package com.healthsaas.zewapulseox;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.provider.SyncStateContract;
import android.telephony.TelephonyManager;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.WindowManager;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.DialogFragment;
import androidx.fragment.app.FragmentActivity;
import androidx.fragment.app.FragmentManager;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;

import com.healthsaas.edge.safe.SafeSDK;
import com.healthsaas.zewapulseox.zewa.dataModels.deviceObject;
import com.healthsaas.zewapulseox.zewa.pulseox.PulseOxSettings;
import com.healthsaas.zewapulseox.zewadb.BtDeviceDb;

import static com.healthsaas.zewapulseox.zewa.pulseox.PulseOxSettings.checkForNewSettings;


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
    public static final String RECEIVE_MSG = "com.healthsaas.pulseox.RECEIVE_MSG";
    final static int PERMISSION_ACCESS_FINE_LOCATION = 0;
    final static int PERMISSION_ACCESS_PHONE_STATE = 1;
    final static int PERMISSION_ACCESS_FILESYSTEM = 2;

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

        getIMEI();
        Log.i(TAG, "Main Activity Launched. RootUrl: " + PulseOxSettings.getRootURL());

        sharedPrefs = this.getApplicationContext().getSharedPreferences(Constants.PREFS_NAME, MODE_PRIVATE);
        setupMode = sharedPrefs.getBoolean("isFirstRun", true);
        if (setupMode)
            sharedPrefs.edit().putBoolean("isFirstRun", false).apply();
        bManager = LocalBroadcastManager.getInstance(this);
        IntentFilter intentFilter = new IntentFilter();
        intentFilter.addAction(RECEIVE_MSG);
        bManager.registerReceiver(bReceiver, intentFilter);

        mBtDb = new BtDeviceDb(ZewaPulseOxApp.getAppContext());
        mBtDb.open();
        mDevices = new ArrayList<>();
        mBtDb.getAllDevices(mDevices);
        pairedDevices = mDevices.size();

        updateDisplay();
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
    }

    @Override
    protected void onResume() {
        super.onResume();
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
        bManager.unregisterReceiver(bReceiver);
        super.onDestroy();
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
                sList.add(dev.serialNumber);
            }
            serialNumbers = sList.toArray(new CharSequence[sList.size()]);
            showRemoveDialog();
            invalidateOptionsMenu();
            return true;
        }
        else  if (id == R.id.action_settings_restart) {
            ZewaPulseOxExceptionHandler.recycleApp(-5);
            return true;
        }
        else if (id == R.id.action_home) {
            goHome();
        }

        return super.onOptionsItemSelected(item);
    }

    private void goHome() {
        Intent launchIntent = getPackageManager().getLaunchIntentForPackage("net.healthsaas.edge");
        if (launchIntent != null) {//null pointer check in case package name was not found
            launchIntent.addFlags(Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED | Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS);
            startActivity(launchIntent);
        }
    }

    private void updateDisplay() {
        int connections = sharedPrefs.getInt("TotalConnections", 1);
        //float avgConnectTime = (float)sharedPrefs.getLong("TotalConnectTime", 0) / connections;
        long lastConnectTime = sharedPrefs.getLong("LastConnectTime",0);
        tvConnections.setText(String.format(Locale.US, "Connections: %d", connections));
        tvLastConnectTime.setText(String.format(Locale.US, "Last: %s", Utils.getISO8601(lastConnectTime)));
        //tvConnectionInfo.setText(String.format(Locale.US, "Avg. Conn. Time: %.3fs", avgConnectTime / 1000));
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
        checkScanningPermissions();
    }

    private void getIMEI() {
        checkPhoneStatePermissions();
    }

    private void _startScanning() {
        mHandler.postDelayed(doStopScanning, 30000);
        mIsScanning = true;
        invalidateOptionsMenu();

        sendZewaManagerIntent(Constants.CMD_BT_PAIR, "");

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

    private Runnable doStopScanning = new Runnable() {
        @Override
        public void run() {
            stopScanning();
        }
    };

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

    private static void sendZewaManagerIntent(int command, String btMac) {
        Bundle bundle = new Bundle();
        bundle.putInt(ZewaPulseOxManager.KEY_CMD_ID, command);
        bundle.putString(ZewaPulseOxManager.KEY_MODEL_NAME, Constants.ZEWA_MODEL_PULSEOX);
        bundle.putString(ZewaPulseOxManager.KEY_BT_MAC, btMac);

        Intent intent = new Intent(ZewaPulseOxApp.getAppContext(), ZewaPulseOxManager.class);
        intent.setAction(ZewaPulseOxManager.ACTION_NFC_EVENT)
                .putExtras(bundle);

        ZewaPulseOxApp.getAppContext().startService(intent);
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
                                        .setMessage("Remove this PulseOx: " + RemoveSerialNumber)
                                        .setPositiveButton(R.string.button_remove, new DialogInterface.OnClickListener() {
                                            public void onClick(DialogInterface dialog, int id) {
                                                // Remove this MedWell!
                                                sendZewaManagerIntent(Constants.CMD_BT_UNPAIR, RemoveSerialNumber);
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

}
