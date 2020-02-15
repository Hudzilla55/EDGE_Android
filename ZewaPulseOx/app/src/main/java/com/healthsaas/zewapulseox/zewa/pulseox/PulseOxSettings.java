package com.healthsaas.zewapulseox.zewa.pulseox;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Environment;
import android.util.Log;

import com.healthsaas.zewapulseox.BuildConfig;
import com.healthsaas.zewapulseox.Constants;
import com.healthsaas.zewapulseox.ZewaPulseOxApp;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;

import static android.content.Context.MODE_PRIVATE;

public final class PulseOxSettings {
    public final static String TAG = PulseOxSettings.class.getSimpleName();
    private final static String FILE_NAME = "ZewaPulseOxSettings.txt";
    private final static String ENDPOINT_SETTING_NAME = "endpointRootURL";
    private final static String SCAN_AGGRESSIVE_SETTING_NAME = "scanAggressive";
    private final static String MAX_PAYLOAD_SIZE_SETTING_NAME = "maxPayloadSize";

    public static void checkForNewSettings() {
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
                    } else if (line.startsWith(MAX_PAYLOAD_SIZE_SETTING_NAME)) {
                        line = line.replace(MAX_PAYLOAD_SIZE_SETTING_NAME + "::","");
                        setMaxPayloadSize(Integer.parseInt(line));
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
        Context context = ZewaPulseOxApp.getAppContext();
        SharedPreferences sharedPrefs = context.getSharedPreferences(Constants.PULSEOX_PREFS_NAME, MODE_PRIVATE);
        sharedPrefs.edit().putString(ENDPOINT_SETTING_NAME, rootURL).apply();
        Log.i(TAG, "Set Root URL = " + rootURL);
    }

    public static String getRootURL() {
        Context context = ZewaPulseOxApp.getAppContext();
        SharedPreferences sharedPrefs = context.getSharedPreferences(Constants.PULSEOX_PREFS_NAME, MODE_PRIVATE);
        return sharedPrefs.getString(ENDPOINT_SETTING_NAME, BuildConfig.rootURL);
    }

    private static void setScanAggressive(boolean scanAggressive) {
        Context context = ZewaPulseOxApp.getAppContext();
        SharedPreferences sharedPrefs = context.getSharedPreferences(Constants.PULSEOX_PREFS_NAME, MODE_PRIVATE);
        sharedPrefs.edit().putBoolean(SCAN_AGGRESSIVE_SETTING_NAME, scanAggressive).apply();
        Log.i(TAG, "Set Scam Aggressive = " + String.valueOf(scanAggressive));
    }

    public static boolean getScanAggressive() {
        Context context = ZewaPulseOxApp.getAppContext();
        SharedPreferences sharedPrefs = context.getSharedPreferences(Constants.PULSEOX_PREFS_NAME, MODE_PRIVATE);
        boolean scanAggressive = sharedPrefs.getBoolean(SCAN_AGGRESSIVE_SETTING_NAME, true);
        Log.i(TAG, "Get Scan Aggressive = " + String.valueOf(scanAggressive));
        return scanAggressive;
    }

    private static void setMaxPayloadSize(int maxPayloadSize) {
        Context context = ZewaPulseOxApp.getAppContext();
        SharedPreferences sharedPrefs = context.getSharedPreferences(Constants.PREFS_NAME, MODE_PRIVATE);
        sharedPrefs.edit().putInt(MAX_PAYLOAD_SIZE_SETTING_NAME, maxPayloadSize).apply();
        Log.i(TAG, "Set Max Payload Size = " + String.valueOf(maxPayloadSize));
    }

    public static int getMaxPayloadSize() {
        Context context = ZewaPulseOxApp.getAppContext();
        SharedPreferences sharedPrefs = context.getSharedPreferences(Constants.PREFS_NAME, MODE_PRIVATE);
        int maxPayloadSize = sharedPrefs.getInt(MAX_PAYLOAD_SIZE_SETTING_NAME, 144);
        Log.i(TAG, "Get Max Payload Size = " + String.valueOf(maxPayloadSize));
        return maxPayloadSize;
    }
}
