package com.healthsaas.zewals;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
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
import android.telephony.TelephonyManager;
import android.text.InputType;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.widget.EditText;
import android.widget.TextView;

import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;

import androidx.annotation.Nullable;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import com.healthsaas.edge.safe.SafeSDK;
import com.lifesense.ble.LsBleManager;
import com.lifesense.ble.bean.LsDeviceInfo;
import com.lifesense.ble.bean.constant.OperationCommand;
import com.lifesense.ble.bean.constant.PairingInputRandomStatus;

import static com.healthsaas.edge.safe.AppSettings.getResourceString;
import static com.healthsaas.zewals.ZewaLSSettings.checkForNewSettings;


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

public class MainActivity extends Activity {
    private static final String TAG = MainActivity.class.getSimpleName();
    public static final String RECEIVE_MSG = "com.healthsaas.zewals.RECEIVE_MSG";
    public static final String PROMPT_FOR_PAIR_CODE_MSG = "com.healthsaas.zewals.PROMPT_FOR_PAIR_CODE_MSG";
    final static int PERMISSION_ACCESS_FINE_LOCATION = 0;
    final static int PERMISSION_ACCESS_PHONE_STATE = 1;
    final static int PERMISSION_ACCESS_FILESYSTEM = 2;

    int pairedDevices = 0;
    List<String> pairedBP = new ArrayList<>();
    List<String> pairedAT = new ArrayList<>();
    List<String> pairedWT = new ArrayList<>();
    HashMap<String, String> device2MAC = new HashMap<>();
    SharedPreferences sharedPrefs;
    LocalBroadcastManager bManager;
    TextView tvConnections;
    TextView tvConnectionInfo;
    TextView tvLastConnectTime;
    private boolean mIsScanning = false;
    private boolean mBPScanning = false;
    private boolean mATScanning = false;
    private boolean mWTScanning = false;
    private Handler mHandler = new Handler();
    private String searchModel = "";
    private SafeSDK mSafeSDK = SafeSDK.getInstance();
    private String safeStatus = "";
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

        TextView buildSDKVer = findViewById(R.id.lssdkver);
        buildSDKVer.setText(BuildConfig.LS_SDK_VER);

        tvConnections = findViewById(R.id.connections);
        tvConnectionInfo = findViewById(R.id.connection_info);
        tvLastConnectTime = findViewById(R.id.lastConnectTime);

        getIMEI();
        Log.i(TAG, "Main Activity Launched. RootUrl: " + ZewaLSSettings.getRootURL());

        sharedPrefs = this.getApplicationContext().getSharedPreferences(Constants.PREFS_NAME, MODE_PRIVATE);
        setupMode = sharedPrefs.getBoolean("isFirstRun", true);
        if (setupMode)
            sharedPrefs.edit().putBoolean("isFirstRun", false).apply();
        bManager = LocalBroadcastManager.getInstance(this);
        IntentFilter intentFilter = new IntentFilter();
        intentFilter.addAction(RECEIVE_MSG);
        intentFilter.addAction(PROMPT_FOR_PAIR_CODE_MSG);
        bManager.registerReceiver(bReceiver, intentFilter);

        restorePairedDevices();
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
        super.onDestroy();
        bManager.unregisterReceiver(bReceiver);
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        super.onCreateOptionsMenu(menu);
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.menu_main, menu);
        if (mATScanning) {
            menu.findItem(R.id.action_settings_scan_AT).setTitle(R.string.action_settings_scanning);
        }
        if (mBPScanning) {
            menu.findItem(R.id.action_settings_scan_BP).setTitle(R.string.action_settings_scanning);
         }
        if (mWTScanning) {
            menu.findItem(R.id.action_settings_scan_WT).setTitle(R.string.action_settings_scanning);
        }
        if (pairedAT.size() == 0) {
            menu.findItem(R.id.action_settings_remove_AT).setEnabled(false);
        }
        if (pairedBP.size() == 0) {
            menu.findItem(R.id.action_settings_remove_BP).setEnabled(false);
        }
        if (pairedWT.size() == 0) {
            menu.findItem(R.id.action_settings_remove_WT).setEnabled(false);
        }
        if (mIsScanning) {
            menu.findItem(R.id.action_settings_scan_AT).setEnabled(false);
            menu.findItem(R.id.action_settings_scan_BP).setEnabled(false);
            menu.findItem(R.id.action_settings_scan_WT).setEnabled(false);
        }
        if (setupMode || isStandalone) {
            menu.findItem(R.id.action_settings_scan_WT).setVisible(true);
            menu.findItem(R.id.action_settings_scan_BP).setVisible(true);
            menu.findItem(R.id.action_settings_scan_AT).setVisible(true);
            menu.findItem(R.id.action_settings_remove_WT).setVisible(true);
            menu.findItem(R.id.action_settings_remove_AT).setVisible(true);
            menu.findItem(R.id.action_settings_remove_BP).setVisible(true);
        }
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.
        int id = item.getItemId();

        switch (id) {
            case R.id.action_settings_scan_AT:
                searchModel = Constants.ZEWA_MODEL_ACTRKR_GENERIC;
                mATScanning = true;
                startScanning();
                invalidateOptionsMenu();
                break;
            case R.id.action_settings_remove_AT:
                searchModel = Constants.ZEWA_MODEL_ACTRKR_GENERIC;
                 showRemoveDialog("Pedometer");
                break;
            case R.id.action_settings_scan_BP:
                searchModel = Constants.ZEWA_MODEL_BPM_GENERIC;
                mBPScanning = true;
                startScanning();
                invalidateOptionsMenu();
                break;
            case R.id.action_settings_remove_BP:
                searchModel = Constants.ZEWA_MODEL_BPM_GENERIC;
                showRemoveDialog("BPM");
                break;
            case R.id.action_settings_scan_WT:
                searchModel = Constants.ZEWA_MODEL_WS_GENERIC;
                mWTScanning = true;
                startScanning();
                invalidateOptionsMenu();
                break;
            case R.id.action_settings_remove_WT:
                searchModel = Constants.ZEWA_MODEL_WS_GENERIC;
                showRemoveDialog("Weight Scale");
                break;
            case R.id.action_settings_restart:
                ZewaLSExceptionHandler.recycleApp(-5);
                break;
            case R.id.action_home:
                goHome();

        }
        return super.onOptionsItemSelected(item);
    }

    private void goHome() {
        mHandler.removeCallbacks(homeTimeout);
        Intent launchIntent = getPackageManager().getLaunchIntentForPackage("net.healthsaas.edge");
        if (launchIntent != null) {//null pointer check in case package name was not found
            launchIntent.addFlags(Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED | Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS);
            startActivity(launchIntent);
        }
    }

    private void updateDisplay() {
        int connections = sharedPrefs.getInt("TotalConnections", 1);
        long lastConnectTime = sharedPrefs.getLong("LastConnectTime",0);
        tvConnections.setText(String.format(Locale.US, "Connections: %d", connections));
        tvLastConnectTime.setText(String.format(Locale.US, "Last: %s", Utils.getISO8601(lastConnectTime)));
        tvConnectionInfo.setText(String.format(Locale.US, "Paired BPM: %d\nPaired Pedometer: %d\nPaired Scale: %d", pairedBP.size(), pairedAT.size(), pairedWT.size()));
    }

    private BroadcastReceiver bReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            _onReceive(intent);
        }

        private void _onReceive(Intent intent) {
            String action = intent.getAction();
            Log.i(TAG, "_onReceive: " + action);
            switch (action) {
                case RECEIVE_MSG:
                    updateDisplay();
                    break;
                case PROMPT_FOR_PAIR_CODE_MSG:
                    final String macAddress = intent.getStringExtra("macAddress");
                    showRandomNumberInputView(macAddress);
                    break;
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

    private void sendZewaManagerIntent(int command, String btMac) {
        sendZewaManagerIntent(command, btMac, "");
    }

    private void sendZewaManagerIntent(int command, String btMac, String code) {
        Bundle bundle = new Bundle();
        bundle.putInt(ZewaLSManager.KEY_CMD_ID, command);
        bundle.putString(ZewaLSManager.KEY_MODEL_NAME, searchModel);
        bundle.putString(ZewaLSManager.KEY_BT_MAC, btMac);
        bundle.putString(ZewaLSManager.KEY_BT_CODE, code);

        Intent intent = new Intent(ZewaLSApp.getAppContext(), ZewaLSManager.class);
        intent.setAction(ZewaLSManager.ACTION_NFC_EVENT)
                .putExtras(bundle);

        ZewaLSApp.getAppContext().startService(intent);
    }

    private void stopScanning () {
        if (mIsScanning) {
            mIsScanning = false;
            mATScanning = false;
            mWTScanning = false;
            mBPScanning = false;
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
            _pollStatus();
        }

        private synchronized void _pollStatus() {
            String newStatus = mSafeSDK.getStatus();
            if (!newStatus.equals(safeStatus)) {
                if (newStatus.equals(Constants.BT_STATUS_PAIR_OK)
                        || newStatus.equals(Constants.BT_STATUS_PAIR_NOT_FOUND)
                        || newStatus.equals(Constants.BT_STATUS_SEARCHING_CANCEL)
                        || newStatus.equals(Constants.BT_STATUS_PAIR_FAIL)) {
                    stopScanning();
                    restorePairedDevices();
                    updateDisplay();
                } else if (newStatus.equals(Constants.BT_STATUS_UNPAIR_OK)) {
                    invalidateOptionsMenu();
                }
                safeStatus = newStatus;
                updateDisplay();
                updateSafeStatus();
            }
            mHandler.postDelayed(pollStatus, 250);
        }
    };

    private Runnable homeTimeout = new Runnable() {
        @Override
        public void run() {
            goHome();
        }
    };

    public void showRemoveDialog(final String model) {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                switch (model) {
                    case "BPM":
                        serialNumbers = pairedBP.toArray(new CharSequence[pairedBP.size()]);
                        break;
                    case "Pedometer":
                        serialNumbers = pairedAT.toArray(new CharSequence[pairedAT.size()]);
                        break;
                    case "Weight Scale":
                        serialNumbers = pairedWT.toArray(new CharSequence[pairedWT.size()]);
                        break;
                }
                AlertDialog.Builder builder = new AlertDialog.Builder(MainActivity.this);
                builder.setTitle(R.string.select_device_to_remove)
                        .setNegativeButton(R.string.button_cancel, new DialogInterface.OnClickListener() {
                            public void onClick(DialogInterface dialog, int id) {
                                // User cancelled the dialog
                            }
                        })
                        .setItems(serialNumbers, new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialog, int which) {
                                final String RemoveDeviceSerialNumber = serialNumbers[which].toString();
                                AlertDialog.Builder builder2 = new AlertDialog.Builder(MainActivity.this);
                                builder2.setTitle(R.string.confirm_device_to_remove)
                                        .setMessage("Remove this " + model + ": " + RemoveDeviceSerialNumber)
                                        .setPositiveButton(R.string.button_remove, new DialogInterface.OnClickListener() {
                                            public void onClick(DialogInterface dialog, int id) {
                                                // Remove this Device!
                                                sendZewaManagerIntent(Constants.CMD_BT_UNPAIR, MACFromSerialNumber(RemoveDeviceSerialNumber));
                                                pairedDevices--;
                                                switch (model) {
                                                    case "BPM":
                                                        pairedBP.remove(RemoveDeviceSerialNumber);
                                                        break;
                                                    case "Pedometer":
                                                        pairedAT.remove(RemoveDeviceSerialNumber);
                                                        break;
                                                    case "Weight Scale":
                                                        pairedWT.remove(RemoveDeviceSerialNumber);
                                                        break;
                                                }
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

    synchronized void restorePairedDevices() {
        final SharedPreferences sharedPrefs = this.getApplicationContext().getSharedPreferences(Constants.PREFS_NAME, MODE_PRIVATE);
        final String json = sharedPrefs.getString(Constants.PREFS_PAIRED_DEVICES, null);
        if (json == null || json.isEmpty())
            return;
        final Gson gson = new Gson();
        final Type type = new TypeToken<HashMap<String, LsDeviceInfo>>() { }.getType();
        HashMap<String, LsDeviceInfo> mBtMacPairedDeviceMap = gson.fromJson(json, type);
        pairedBP.clear();
        pairedAT.clear();
        pairedWT.clear();
        pairedDevices = 0;
        for (LsDeviceInfo lsDeviceInfo : mBtMacPairedDeviceMap.values()) {
            Log.i(TAG, "restorePairedDevices: " + lsDeviceInfo.getDeviceId() + " - " + lsDeviceInfo.getDeviceType());
            switch (lsDeviceInfo.getDeviceType()) {
                case "01": // weight
                    pairedWT.add(lsDeviceInfo.getDeviceId());
                    break;
                case "04": // pedo
                    pairedAT.add(lsDeviceInfo.getDeviceId());
                    break;
                case "08": // bpm
                    pairedBP.add(lsDeviceInfo.getDeviceId());
                    break;
                default:
                    Log.d(TAG, "restorePairedDevices: unknown device type");
                    break;
            }
            device2MAC.put(lsDeviceInfo.getDeviceId(), lsDeviceInfo.getMacAddress());
            pairedDevices++;
        }
    }

    private String MACFromSerialNumber(String sn) {
        return  device2MAC.get(sn);
    }

    private String randomNumber = "null";
    public AlertDialog.Builder builder;
    public void showRandomNumberInputView(final String macAddress)
    {
        Log.i(TAG, "showRandomNumberInputView: " + macAddress);
        String title=MainActivity.this.getResources().getString(R.string.title_input_random_number);
        // show a dialog to accept input of type numeric.
        builder = new AlertDialog.Builder(MainActivity.this);
        builder.setTitle(title);
        //builder.setMessage(R.string.label_random_number);
        final EditText input = new EditText(MainActivity.this);
        input.setInputType(InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_VARIATION_NORMAL);

        builder.setView(input);

        // Set up the buttons
        builder.setPositiveButton("OK", new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                // get value input into dialog.
                randomNumber = input.getText().toString();

                // check the random number.
                int statue = LsBleManager.getInstance().inputOperationCommand(macAddress, OperationCommand.CMD_RANDOM_NUMBER, randomNumber);
                if(statue == PairingInputRandomStatus.SUCCESS){
                    return ;
                }
                String msg;
                // status fail -- alert user and re-prompt for dialog
                if(statue == PairingInputRandomStatus.FAIL_CHECK_RANDOM_CODE_ERR){
                    msg = getResourceString(R.string.str_random_number_error)+"\n";
                    Log.e(TAG, "showRandomNumberInputView: " + msg );
                    //DialogUtils.showToastMessage(baseContext, msg);
                    //提示重新输入
                    showRandomNumberInputView(macAddress);
                }
                else{
                    msg = "unknown error try again";
                    Log.e(TAG, "showRandomNumberInputView: " + msg );
                    //DialogUtils.showToastMessage(baseContext, msg);
                }

            }
        });
        builder.setNegativeButton("Cancel", new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                sendZewaManagerIntent(Constants.CMD_BT_PAIR_CANCEL, "");
                stopScanning();
            }
        });
        Log.i(TAG, "showRandomNumberInputView.builder.show(): " + macAddress);
        builder.show();
    }


}
