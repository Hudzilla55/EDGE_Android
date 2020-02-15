package net.healthsaas.edge;

import android.Manifest;
import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.content.res.Configuration;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.PowerManager;
import android.provider.Settings;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;
import android.telephony.TelephonyManager;
import android.text.Html;
import android.util.Log;
import android.view.View;
import android.view.Menu;
import android.view.MenuItem;
import android.widget.Button;
import android.widget.TextView;

import com.healthsaas.edge.safe.SafeSDK;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

import static com.healthsaas.edge.safe.AppSettings.checkForNewSettings;


public class MainActivity extends AppCompatActivity {

    private static final String TAG = MainActivity.class.getSimpleName();

    final static int PERMISSION_ACCESS_PHONE_STATE = 1;
    final static int PERMISSION_ACCESS_FILESYSTEM = 2;
    final String activityNameFS = "com.healthsaas.feverscout";
    final String activityNameMW = "com.healthsaas.zewamedwell";
    final String activityNameLS = "com.healthsaas.zewals";
    final String activityNamePO = "com.healthsaas.zewapulseox";
    final String activityNameED = "com.vishs.pn_hphub";
    final String PREFS_NAME = "net.healthsaas.launcher.settings";
    private final Handler mHandler = new Handler();
    private String safeStatus = "Booted";
    private long safeStatusTime = System.currentTimeMillis();
    private PowerManager.WakeLock screenWake;
    private int easterEggClicks = 0;
    private boolean setupMode = false;
    SharedPreferences sharedPrefs;
    private boolean isFirstRun = false;

    private static final SafeSDK safeSDK = SafeSDK.getInstance();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        SetupActivity();
    }

    private void SetupActivity() {
        SafeSDK.getInstance().setIsLauncher();

        setContentView(R.layout.activity_main);

        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        TextView textView2 = findViewById(R.id.textView2);
        textView2.setText(String.format("EDGE v%s - Built: %s", BuildConfig.VERSION_NAME, getISO8601(BuildConfig.TIMESTAMP)));
        TextView tvPoweredBy = findViewById(R.id.poweredBy);
        tvPoweredBy.setText(getText(R.string.poweredBy));

        sharedPrefs = this.getApplicationContext().getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
        setupMode = isFirstRun = sharedPrefs.getBoolean("isFirstRun", true);
        sharedPrefs.edit().putBoolean("setupMode", setupMode).apply();
        if (setupMode) {
            easterEggClicks = 7;
            mHandler.postDelayed(setupModeTimeout, 600000);
        }
        getIMEI();

        Button btn911 = findViewById(R.id.button3);
        //if (!SafeSDK.getInstance().getIMEI().isEmpty()) {
        //btn911.setOnClickListener(new View.OnClickListener() {
        //    @Override
        //    public void onClick(View view) {
        //        call911_verify();
        //    }
        //});
        //} else {
        btn911.setVisibility(View.INVISIBLE);
        //}
        //this.startLockTask();
    }

/*
    @Override
    public void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        setContentView(R.layout.activity_main);
        Log.i(TAG, "onConfigurationChanged: ");
        SetupActivity();
    }
*/

    @Override
    protected void onResume() {
        mHandler.removeCallbacks(pollStatus);
        mHandler.postDelayed(pollStatus, 1);
        setupMode = sharedPrefs.getBoolean("setupMode", false);
        invalidateOptionsMenu();
        super.onResume();
    }

    @Override
    protected void onPause() {
        sharedPrefs.edit().putBoolean("setupMode", setupMode).apply();
        super.onPause();
    }

    @Override
    protected void onDestroy() {
        mHandler.removeCallbacks(pollStatus);
        mHandler.removeCallbacks(homeTimeout);
        sharedPrefs.edit().putBoolean("setupMode", false).apply();
        super.onDestroy();
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.menu_main, menu);

        if (getPackageManager().getLaunchIntentForPackage(activityNameED) == null) {
            menu.findItem(R.id.action_education).setVisible(false);
        }

        if (getPackageManager().getLaunchIntentForPackage(activityNameFS) == null) {
            menu.findItem(R.id.action_feverscout).setVisible(false);
        }

        if (getPackageManager().getLaunchIntentForPackage(activityNameMW) == null) {
            menu.findItem(R.id.action_medwell).setVisible(false);
        }

        if (getPackageManager().getLaunchIntentForPackage(activityNamePO) == null) {
            menu.findItem(R.id.action_pulseox).setVisible(false);
        }

        if (getPackageManager().getLaunchIntentForPackage(activityNameLS) == null) {
            menu.findItem(R.id.action_zewals).setVisible(false);
        }

        if (setupMode) {
            menu.findItem(R.id.action_settings).setVisible(true);
            menu.findItem(R.id.action_settings_bt).setVisible(true);
            menu.findItem(R.id.action_settings_datetime).setVisible(true);
            menu.findItem(R.id.action_settings_wifi).setVisible(true);
        }
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        Intent intent;
        mHandler.removeCallbacks(homeTimeout);
        mHandler.postDelayed(homeTimeout, 120000 + (setupMode ? 480000 : 0));
        switch (item.getItemId()) {
            case R.id.action_education:
                mHandler.removeCallbacks(homeTimeout);
                launchActivity(activityNameED);
                break;
            case R.id.action_feverscout:
                launchActivity(activityNameFS);
                break;
            case R.id.action_medwell:
                launchActivity(activityNameMW);
                break;
            case R.id.action_pulseox:
                launchActivity(activityNamePO);
                break;
            case R.id.action_zewals:
                launchActivity(activityNameLS);
                break;
            case R.id.action_settings:
                intent = new Intent(android.provider.Settings.ACTION_SETTINGS);
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                startActivity(intent);
                break;
            case R.id.action_settings_wifi:
                intent = new Intent(android.provider.Settings.ACTION_WIFI_SETTINGS);
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                startActivity(intent);
                break;
            case R.id.action_settings_bt:
                intent = new Intent(android.provider.Settings.ACTION_BLUETOOTH_SETTINGS);
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                startActivity(intent);
                break;
            case R.id.action_settings_datetime:
                intent = new Intent(Settings.ACTION_DATE_SETTINGS);
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                startActivity(intent);
                break;
            case R.id.action_about:
                mHandler.removeCallbacks(homeTimeout);
                ShowAboutDialog();
                break;
        }
        return super.onOptionsItemSelected(item);
    }

    @Override
    public void onRequestPermissionsResult(int requestCode,
                                           String permissions[], int[] grantResults) {
        switch (requestCode) {
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

        }
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

    private void getIMEI() {
        checkPhoneStatePermissions();
    }

    private void _getIMEI() {
        try {
            TelephonyManager telephonyManager = (TelephonyManager)getSystemService(Context.TELEPHONY_SERVICE);
            String IMEI = telephonyManager.getDeviceId();
            if (IMEI != null)
                SafeSDK.getInstance().setIMEI(IMEI);
            SafeSDK.getInstance().setSerialNumber(Build.SERIAL);
        } catch (SecurityException ex) {
            Log.d(TAG, "_getIMEI: " + ex.toString());
        }
    }

    private void launchActivity(String activityName) {
        Intent launchIntent = getPackageManager().getLaunchIntentForPackage(activityName);
        if (launchIntent != null) {//null pointer check in case package name was not found
            Bundle b = new Bundle();
            b.putBoolean("setupMode", setupMode);
            b.putBoolean("isBooting", false);
            launchIntent.putExtras(b);
            launchIntent.addFlags(Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED | Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS);
            startActivity(launchIntent);
        }
    }

    public static String getISO8601(long timeStamp) {
        return getTimeString(timeStamp,"yyyy-MM-dd HH:mm:ss.SSSZZZZZ");
    }

    public static String getTimeString(long timeStamp, String format) {
        String resp;
        SimpleDateFormat sdf = new SimpleDateFormat(format, Locale.US);
        resp = sdf.format(new Date(timeStamp));
        return resp;
    }

    private void call911() {
        String str = "tel:911";
        Intent intent = new Intent(Intent.ACTION_CALL);
        intent.setData(Uri.parse(str));
        startActivity(intent);
    }

    private void call911_verify() {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                String msg = "Call 911?\n\nAre you sure you want to call 911?\n\nClick on 'Call 911!' to continue\n\nClick on 'Cancel' to cancel.";
                AlertDialog.Builder builder = new AlertDialog.Builder(
                        MainActivity.this)
                        .setTitle("Call 911?")
                        .setNegativeButton(("Call 911!"),
                                new DialogInterface.OnClickListener() {
                                    @Override
                                    public void onClick(
                                            DialogInterface dialog,
                                            int which) {
                                        call911();
                                    }
                                })
                        .setPositiveButton("Cancel",
                                new DialogInterface.OnClickListener() {
                                    @Override
                                    public void onClick(
                                            DialogInterface dialog,
                                            int which) {
                                        dialog.cancel();
                                    }

                                }).setMessage(msg);

                builder.create().show();
            }
        });

    }

    private void updateSafeStatus() {
        TextView status = findViewById(R.id.statusText);
        TextView statusTime = findViewById(R.id.statusText2);
        status.setText(safeStatus);;
        statusTime.setText(getTimeString(safeStatusTime, "EEE, MMM d, yyyy\nh:mm:ss a z"));
        unlockScreen();
    }

    private Runnable pollStatus = new Runnable() {
        @Override
        public void run() {
            _pollStatus();
        }

        private void _pollStatus() {
            String newStatus = safeSDK.getStatus();
            long newStatusTime = safeSDK.getStatusTime();
            if (!newStatus.equals(safeStatus)  || newStatusTime != safeStatusTime) {
                safeStatus = newStatus;
                safeStatusTime = newStatusTime;
                updateSafeStatus();
            }
            mHandler.postDelayed(pollStatus, 250);
        }
    };

    private void unlockScreen() {
        PowerManager pm = (PowerManager) this.getSystemService(Context.POWER_SERVICE);
        if (screenWake != null && screenWake.isHeld())
            screenWake.release();
        screenWake = pm.newWakeLock( PowerManager.FULL_WAKE_LOCK | PowerManager.ACQUIRE_CAUSES_WAKEUP | PowerManager.ON_AFTER_RELEASE, TAG +":MyWakeLock");
        screenWake.acquire(60000);
    }

    private void goHome() {
        mHandler.removeCallbacks(homeTimeout);
        Intent launchIntent = getPackageManager().getLaunchIntentForPackage("net.healthsaas.edge");
        if (launchIntent != null) {//null pointer check in case package name was not found
            launchIntent.addFlags(Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED | Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS);
            startActivity(launchIntent);
        }
    }

    private Runnable homeTimeout = new Runnable() {
        @Override
        public void run() {
            goHome();
        }
    };

    public void ShowAboutDialog() {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                CharSequence[] items = new CharSequence[4];
                items[0] = getText(R.string.app_name);
                items[1] = Html.fromHtml("IMEI: <b>" + SafeSDK.getInstance().getIMEI() + "</b>");
                items[2] = Html.fromHtml("SN: <b>"  + SafeSDK.getInstance().getSerialNumber().toUpperCase() + "</b>") ;
                items[3] = getText(R.string.copyright);
                AlertDialog.Builder builder = new AlertDialog.Builder(MainActivity.this);
                builder.setTitle(R.string.about_title)
                        .setPositiveButton(R.string.button_ok, new DialogInterface.OnClickListener() {
                            public void onClick(DialogInterface dialog, int id) {
                                // User cancelled the dialog
                                cancelSetupMode();
                            }
                        })
                        .setItems(items, new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialog, int which) {
                                if (which == 1 || which == 2) {
                                    easterEggClicks++;
                                    if (easterEggClicks < 5)
                                        ShowAboutDialog();
                                } else {
                                    easterEggClicks = 0;
                                    ShowAboutDialog();
                                }
                                if (easterEggClicks >= 7) {
                                    AlertDialog.Builder builder2 = new AlertDialog.Builder(MainActivity.this);
                                    builder2.setTitle(R.string.confirm_setup_mode)
                                            .setMessage("Enter into Setup Mode?\n\nThis is not recommended")
                                            .setPositiveButton(R.string.button_enable, new DialogInterface.OnClickListener() {
                                                public void onClick(DialogInterface dialog, int id) {
                                                    setupMode = true;
                                                    sharedPrefs.edit().putBoolean("setupMode", true).apply();
                                                    invalidateOptionsMenu();
                                                    mHandler.postDelayed(setupModeTimeout, 600000);
                                                }
                                            })
                                            .setNegativeButton(R.string.button_cancel, new DialogInterface.OnClickListener() {
                                                public void onClick(DialogInterface dialog, int id) {
                                                    // User cancelled the dialog
                                                    cancelSetupMode();
                                                }
                                            });
                                    builder2.create().show();
                                }
                            }
                        });
                builder.create().show();
            }
        });
    }

    private Runnable setupModeTimeout = new Runnable() {
        @Override
        public void run() {
            cancelSetupMode();
       }
    };

    private void cancelSetupMode() {
        if (isFirstRun)
            sharedPrefs.edit().putBoolean("isFirstRun", false).apply();
        sharedPrefs.edit().putBoolean("setupMode", false).apply();
        easterEggClicks = 0;
        setupMode = isFirstRun = false;
        invalidateOptionsMenu();
    }

}
