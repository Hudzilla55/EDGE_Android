package com.healthsaas.zewamedwell;

import android.util.Log;

import com.healthsaas.zewamedwell.zewa.dataModels.deviceObject;
import com.healthsaas.zewamedwell.zewa.medwell.MedWellSettings;
import com.healthsaas.zewamedwell.zewadb.BtDeviceDb;
import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

import java.io.BufferedReader;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.ArrayList;

import javax.net.ssl.HttpsURLConnection;

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

public class MedWellReminderGetTask extends Thread {
    private static final String TAG = MedWellReminderGetTask.class.getSimpleName();

    private static final String defaultHttpMethod = "POST";
    private static final String defaultContentType = "json";

    private final String httpMethod;
    private final String rootUrl;
    private final String contentType;

    private ArrayList<deviceObject> mDevices;
    private BtDeviceDb mBtDb;
    private String devMac = "";

    public MedWellReminderGetTask() {
        this(defaultHttpMethod, defaultContentType, "");
    }

    MedWellReminderGetTask(String devMac) {
        this(defaultHttpMethod, defaultContentType, devMac);
    }

    private MedWellReminderGetTask(String httpMethod, String contentType, String devMac) {
        this.httpMethod = httpMethod;
        this.rootUrl = MedWellSettings.getRootURL();
        this.contentType = contentType;
        this.mDevices = new ArrayList<>();
        this.devMac = devMac;
    }

    @Override
    public void run() {
        mBtDb = ZewaMedWellManager.getMgrInstance().getBtDb();
        mDevices.clear();
        mBtDb.getAllDevices(mDevices);
        for (deviceObject dev: mDevices) {
            if (devMac.isEmpty()) {
                if (getMedWellSortVersion(dev))
                    getMedWellReminder(dev);
            } else if (devMac.equals(dev.macAddress)) {
                if (getMedWellSortVersion(dev))
                    getMedWellReminder(dev);
            }
        }
    }

    public boolean getMedWellReminder(deviceObject dev) {
        if (dev == null)
            return  false;
        String endpointURL = String.format("%s%s", this.rootUrl, Constants.MEDWELL_REMINDER_PATH);
        HttpsURLConnection urlConnection = null;
        boolean dev_updated = false;
        boolean doPost = false;
        try {
            JsonObject medwellRequestObj = new JsonObject();
            medwellRequestObj.addProperty(Constants.MEDWELL_REMINDER_REQ_PARAM, dev.serialNumber);
            byte[] medwellRequestBytes = medwellRequestObj.toString().getBytes();

            URL url = new URL(endpointURL);
            urlConnection = (HttpsURLConnection) url.openConnection();
            urlConnection.setDoInput(true);

            if ("json".equals(this.contentType)) {
                urlConnection.setRequestProperty("Content-type", "application/json;charset=UTF-8");
                urlConnection.setRequestProperty("Accept", "application/json");
            }
            if ("POST".equals(this.httpMethod)) {
                urlConnection.setDoOutput(true);
                urlConnection.setFixedLengthStreamingMode(medwellRequestBytes.length);
                doPost = true;
            }
            Log.i(TAG, "getMedWellReminder: Connect: " + endpointURL);
            urlConnection.connect();

            if (doPost) {
                DataOutputStream out = new DataOutputStream(urlConnection.getOutputStream());
                out.write(medwellRequestBytes);
                out.flush();
                out.close();
            }

            int httpResponseCode = urlConnection.getResponseCode();
            Log.d(TAG, String.format(" HTTP Response: RC=%d, <%s>",
                    httpResponseCode,
                    urlConnection.getResponseMessage()));
            if (httpResponseCode == HttpURLConnection.HTTP_OK) {
                BufferedReader br = new BufferedReader(new InputStreamReader(urlConnection.getInputStream()));
                StringBuilder sb = new StringBuilder();
                String line;
                while( (line = br.readLine()) != null) {
                    sb.append(line);
                }
                line = sb.toString();
                Gson gson = new Gson();
                JsonArray medWellReminderArray = gson.fromJson(line,JsonArray.class);
                if ( medWellReminderArray.size() > 0) {
                    int reminderSortVer = medWellReminderArray.get(0).getAsJsonObject().get("sortVer").getAsInt();
                    if( reminderSortVer > dev.sortVer) {
                        dev.sortVer = reminderSortVer;
                        dev.extraInfoJson = line;
                        dev.sortUpdatedTime = System.currentTimeMillis();
                        dev_updated = true;
                    }
                } else {
                    dev.sortVer = 0;
                    if ( dev.extraInfoJson != null && ! dev.extraInfoJson.isEmpty() ) {
                        dev_updated = true;
                    }
                    dev.extraInfoJson = "";
                }
                dev.sortSyncedTime = System.currentTimeMillis();
            } else {
                readErrorMsg(urlConnection);
            }

        } catch (MalformedURLException e) {
            Log.e(TAG, String.format("Invalid url=<%s>", endpointURL));
        } catch (IOException e) {
            Log.e(TAG, String.format("IOException from URL <%s>", endpointURL));
            if (urlConnection != null) {
                readErrorMsg(urlConnection);
            }
        } finally {
            if (urlConnection != null) {
                urlConnection.disconnect();
            }
        }
        if (dev_updated) {
            if (mBtDb == null)
                mBtDb = ZewaMedWellManager.getMgrInstance().getBtDb();
            mBtDb.update(dev);
        }
        return dev_updated;
    }

    private boolean getMedWellSortVersion(deviceObject dev) {
        if (dev == null)
            return  false;
        String endpointURL = String.format("%s%s", this.rootUrl, Constants.MEDWELL_SORTVER_PATH);
        HttpsURLConnection urlConnection = null;
        boolean dev_updated = false;
        boolean doPost = false;
        try {
            JsonObject medwellRequestObj = new JsonObject();
            medwellRequestObj.addProperty(Constants.MEDWELL_REMINDER_REQ_PARAM, dev.serialNumber);
            byte[] medwellRequestBytes = medwellRequestObj.toString().getBytes();

            URL url = new URL(endpointURL);
            urlConnection = (HttpsURLConnection) url.openConnection();
            urlConnection.setDoInput(true);

            if ("json".equals(this.contentType)) {
                urlConnection.setRequestProperty("Content-type", "application/json; charset=UTF-8");
                urlConnection.setRequestProperty("Accept", "application/json");
            }
            if ("POST".equals(this.httpMethod)) {
                urlConnection.setDoOutput(true);
                urlConnection.setFixedLengthStreamingMode(medwellRequestBytes.length);
                doPost = true;
            }
            Log.i(TAG, "getMedWellSortVersion: Connect: " + endpointURL);
            urlConnection.connect();

            if (doPost) {
                DataOutputStream out = new DataOutputStream(urlConnection.getOutputStream());
                out.write(medwellRequestBytes);
                out.flush();
                out.close();
            }

            int httpResponseCode = urlConnection.getResponseCode();
            Log.d(TAG, String.format(" HTTP Response: RC=%d, <%s>",
                    httpResponseCode,
                    urlConnection.getResponseMessage()));
            if (httpResponseCode == HttpURLConnection.HTTP_OK) {
                BufferedReader br = new BufferedReader(new InputStreamReader(urlConnection.getInputStream()));
                StringBuilder sb = new StringBuilder();
                String line;
                while( (line = br.readLine()) != null) {
                    sb.append(line);
                }
                int newVer = Integer.parseInt(sb.toString());
                if (newVer > dev.sortVer) {
                    dev_updated = true;
                    dev.timeZoneOffset = "";
                    dev.extraInfoJson = "";
                    dev.sortSyncedTime = System.currentTimeMillis();
                }
            } else {
                readErrorMsg(urlConnection);
            }

        } catch (MalformedURLException e) {
            Log.e(TAG, String.format("Invalid url=<%s>", endpointURL));
        } catch (IOException e) {
            Log.e(TAG, String.format("IOException from URL <%s> :%s", endpointURL, e.getMessage()));
            if (urlConnection != null) {
                readErrorMsg(urlConnection);
            }
        } finally {
            if (urlConnection != null) {
                urlConnection.disconnect();
            }
        }
        return dev_updated;
    }

    private void readErrorMsg(final HttpsURLConnection conn) {
        final int MAX_BUFLEN = 1024;

        if ( conn == null) {
            return;
        }

        try {
            InputStream es = conn.getErrorStream();
            if ( es != null) {
                byte[] buf = new byte[MAX_BUFLEN];
                while (es.read(buf) > 0) {
                    String httpErrMsg = new String(buf, "UTF-8");
                    Log.d(TAG, "HTTP ERRMSG: " + httpErrMsg);
                    java.util.Arrays.fill(buf, (byte) 0);
                }
            }
        } catch (IOException e) {
            Log.e(TAG, "Failed to read HTTP error msg");
        }
    }
}
