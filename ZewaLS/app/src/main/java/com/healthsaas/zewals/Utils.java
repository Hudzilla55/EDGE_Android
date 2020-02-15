package com.healthsaas.zewals;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.Locale;
import java.util.TimeZone;

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

public class Utils {
    /**
     * Reformat measurement time into ISO8601 format
     * <p>
     * measurementTime - "yyyy-MM-dd hh:mm:ss"
     * <p>
     * returns "yyyy-MM-dd hh:mm:ss-hh:mm"
     */
    static String fmtISO8601_measurementTime(String shortDateTime) {
        return shortDateTime + getShortDateTimeZoneOffset(shortDateTime);
    }

    static String fmtISO8601_receiptTime() {
        return getISO8601(System.currentTimeMillis());
    }

    static String getShortDateTimeZoneOffset(String shortDateTime) {
        String resp = "";
        SimpleDateFormat sdf = new SimpleDateFormat(Constants.ISO8601_SHORT_FORMAT_STRING, Locale.US);
        Calendar dateTime = Calendar.getInstance();
        try {
            dateTime.setTime(sdf.parse(shortDateTime));
            resp =  getTimeZoneOffset(dateTime.getTime());
        } catch (ParseException e) {
            e.printStackTrace();
        }
        return  resp;
    }

    static String getTimeZoneOffSet() {
        return getTimeZoneOffset(new Date(System.currentTimeMillis()));
    }
    static String getTimeZoneOffset(String longDateTime) {
        String resp = "";
        SimpleDateFormat sdf = new SimpleDateFormat(Constants.ISO8601_FORMAT_STRING, Locale.US);
        Calendar dateTime = Calendar.getInstance();
        try {
            dateTime.setTime(sdf.parse(longDateTime));
            resp =  getTimeZoneOffset(dateTime.getTime());
        } catch (ParseException e) {
            e.printStackTrace();
        }
        return  resp;
    }

    public static String getISO8601(long timeStamp) {
        String resp;
        SimpleDateFormat sdf = new SimpleDateFormat(Constants.ISO8601_FORMAT_STRING, Locale.US);
        resp = sdf.format(new Date(timeStamp));
        return resp;
    }

    public static long getShortDateTimeSeconds(String shortDateTime) {
        long resp = 0;
        SimpleDateFormat sdf = new SimpleDateFormat(Constants.ISO8601_SHORT_FORMAT_STRING, Locale.US);
        Calendar dateTime = Calendar.getInstance();
        try {
            dateTime.setTime(sdf.parse(shortDateTime));
            resp =  dateTime.getTimeInMillis() / 1000;
        } catch (ParseException e) {
            e.printStackTrace();
        }
        return  resp;
    }

    public static int getShortDateOffsetMinutes(String shortDateTime) {
        int resp = 0;
        TimeZone tz = TimeZone.getDefault();
        SimpleDateFormat sdf = new SimpleDateFormat(Constants.ISO8601_SHORT_FORMAT_STRING, Locale.US);
        try {
            resp = tz.getOffset(sdf.parse(shortDateTime).getTime()) / 60000;
        } catch (ParseException e) {
            e.printStackTrace();
        }
        return resp;
    }

    private static String getTimeZoneOffset(Date date) {
        String resp;
        SimpleDateFormat sdf = new SimpleDateFormat(Constants.ISO8601_TZ_FORMAT_STRING, Locale.US);
        resp = sdf.format(date);
        return resp;
    }
}
