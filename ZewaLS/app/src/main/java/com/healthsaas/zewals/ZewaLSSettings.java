package com.healthsaas.zewals;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Environment;
import android.util.Log;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;

import static android.content.Context.MODE_PRIVATE;

class ZewaLSSettings {
    private final static String TAG = ZewaLSSettings.class.getSimpleName();
    private final static String FILE_NAME = "ZewaLSSettings.txt";
    private final static String ENDPOINT_SETTING_NAME = "endpointRootURL";
    private final static String SCAN_AGGRESSIVE_SETTING_NAME = "scanAggressive";
    private final static String MAX_ACTRKR_PAYLOAD_SIZE_SETTING_NAME = "maxActrkrPayloadSize";

    static void checkForNewSettings() {
        Log.i(TAG, "checkForNewSettings");
        File sdcard = Environment.getExternalStorageDirectory();
        File file = new File(sdcard,FILE_NAME);
        if (file.exists()) {
            try {
                BufferedReader br = new BufferedReader(new FileReader(file));
                String line;
                while ((line = br.readLine()) != null) {
                    if (line.startsWith(ENDPOINT_SETTING_NAME)) {
                        line = line.replace(ENDPOINT_SETTING_NAME + "::","");
                        setRootURL(line);
                    } else if (line.startsWith(SCAN_AGGRESSIVE_SETTING_NAME)) {
                        line = line.replace(SCAN_AGGRESSIVE_SETTING_NAME + "::","");
                        setScanAggressive(Boolean.parseBoolean(line));
                    } else if (line.startsWith(MAX_ACTRKR_PAYLOAD_SIZE_SETTING_NAME)) {
                        line = line.replace(MAX_ACTRKR_PAYLOAD_SIZE_SETTING_NAME + "::","");
                        setMaxActrkrPayloadSize(Integer.parseInt(line));
                    }
                }
                br.close();
                if (!file.delete()) {
                    Log.e(TAG, "Failed to delete settings file");
                }
            }
            catch (IOException e) {
                //You'll need to add proper error handling here
                Log.e(TAG, e.getMessage());
            }
        }
    }

    private static void setRootURL(String rootURL) {
        Context context = ZewaLSApp.getAppContext();
        SharedPreferences sharedPrefs = context.getSharedPreferences(Constants.PREFS_NAME, MODE_PRIVATE);
        sharedPrefs.edit().putString(ENDPOINT_SETTING_NAME, rootURL).apply();
        Log.i(TAG, "Set Root URL = " + rootURL);
    }

    static String getRootURL() {
        Context context = ZewaLSApp.getAppContext();
        SharedPreferences sharedPrefs = context.getSharedPreferences(Constants.PREFS_NAME, MODE_PRIVATE);
        return sharedPrefs.getString(ENDPOINT_SETTING_NAME, BuildConfig.rootURL);
    }

    private static void setScanAggressive(boolean scanAggressive) {
        Context context = ZewaLSApp.getAppContext();
        SharedPreferences sharedPrefs = context.getSharedPreferences(Constants.PREFS_NAME, MODE_PRIVATE);
        sharedPrefs.edit().putBoolean(SCAN_AGGRESSIVE_SETTING_NAME, scanAggressive).apply();
        Log.i(TAG, "Set Scam Aggressive = " + String.valueOf(scanAggressive));
    }

    static boolean getScanAggressive() {
        Context context = ZewaLSApp.getAppContext();
        SharedPreferences sharedPrefs = context.getSharedPreferences(Constants.PREFS_NAME, MODE_PRIVATE);
        boolean scanAggressive = sharedPrefs.getBoolean(SCAN_AGGRESSIVE_SETTING_NAME, false);
        Log.i(TAG, "Get Scan Aggressive = " + String.valueOf(scanAggressive));
        return scanAggressive;
    }

    private static void setMaxActrkrPayloadSize(int maxPayloadSize) {
        Context context = ZewaLSApp.getAppContext();
        SharedPreferences sharedPrefs = context.getSharedPreferences(Constants.PREFS_NAME, MODE_PRIVATE);
        sharedPrefs.edit().putInt(MAX_ACTRKR_PAYLOAD_SIZE_SETTING_NAME, maxPayloadSize).apply();
        Log.i(TAG, "Set Max Actrkr Payload Size = " + String.valueOf(maxPayloadSize));
    }

    static int getMaxPayloadSize() {
        Context context = ZewaLSApp.getAppContext();
        SharedPreferences sharedPrefs = context.getSharedPreferences(Constants.PREFS_NAME, MODE_PRIVATE);
        int maxPayloadSize = sharedPrefs.getInt(MAX_ACTRKR_PAYLOAD_SIZE_SETTING_NAME, 48);
        Log.i(TAG, "Get Max Actrkr Payload Size = " + String.valueOf(maxPayloadSize));
        return maxPayloadSize;
    }
}
