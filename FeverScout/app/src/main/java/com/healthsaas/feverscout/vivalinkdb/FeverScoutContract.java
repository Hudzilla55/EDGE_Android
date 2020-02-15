package com.healthsaas.feverscout.vivalinkdb;

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

import android.provider.BaseColumns;

/**
 * define BLE PulseOx Device database schema
 */

public final class FeverScoutContract {
    private FeverScoutContract() {
    }

    public static class PulseOxEntry implements BaseColumns {
        public static final String TABLE_NAME = "PulseOxTable";
        public static final String COLUMN_NAME_SN = "sn";
        public static final String COLUMN_NAME_BT_MAC = "bt_mac";
        public static final String COLUMN_NAME_DEVICE_ID = "dev_id";
        public static final String COLUMN_NAME_MODEL = "model";
        public static final String COLUMN_NAME_PASS_KEY = "pass_key";
        public static final String COLUMN_NAME_LAST_CONTACT = "last_contact";
        public static final String COLUMN_NAME_LAST_STATUS = "last_status";
        public static final String COLUMN_NAME_BATTERY_LEVEL = "battery_level";
        public static final String COLUMN_NAME_USER_ID = "user_id";
        public static final String COLUMN_NAME_USER_PIN = "user_pin";
        public static final String COLUMN_NAME_PAIRED = "paired";
        public static final String COLUMN_NAME_ROOT_URL = "root_url";
        public static final String COLUMN_NAME_TZ_OFFSET = "tz_offset";
        public static final String COLUMN_NAME_EXTRA_INFO_JSON = "extra_info";
        public static final String COLUMN_NAME_OUTSTANDING_SYNC_COUNT = "outstanding_sync_count";
        public static final String COLUMN_NAME_SORT_VER = "sort_ver";
        public static final String COLUMN_NAME_SORT_UPDATED_TIME = "sort_updated_time";
        public static final String COLUMN_NAME_SORT_SYNCED_TIME = "sort_synced_time";
    }

    public static final String SQL_CREATE_ENTRIES =
            "CREATE TABLE " + PulseOxEntry.TABLE_NAME + " (" +
                    PulseOxEntry._ID + " INTEGER PRIMARY KEY," +
                    PulseOxEntry.COLUMN_NAME_SN + " TEXT," +
                    PulseOxEntry.COLUMN_NAME_BT_MAC + " TEXT," +
                    PulseOxEntry.COLUMN_NAME_DEVICE_ID + " TEXT," +
                    PulseOxEntry.COLUMN_NAME_MODEL + " TEXT," +
                    PulseOxEntry.COLUMN_NAME_PASS_KEY + " TEXT," +
                    PulseOxEntry.COLUMN_NAME_LAST_CONTACT + " LONG," +
                    PulseOxEntry.COLUMN_NAME_LAST_STATUS + " TEXT," +
                    PulseOxEntry.COLUMN_NAME_BATTERY_LEVEL + " INTEGER," +
                    PulseOxEntry.COLUMN_NAME_USER_ID + " TEXT," +
                    PulseOxEntry.COLUMN_NAME_USER_PIN + " TEXT," +
                    PulseOxEntry.COLUMN_NAME_PAIRED + " BOOLEAN," +
                    PulseOxEntry.COLUMN_NAME_ROOT_URL + " TEXT," +
                    PulseOxEntry.COLUMN_NAME_TZ_OFFSET + " TEXT," +
                    PulseOxEntry.COLUMN_NAME_EXTRA_INFO_JSON + " TEXT," +
                    PulseOxEntry.COLUMN_NAME_OUTSTANDING_SYNC_COUNT + " INTEGER," +
                    PulseOxEntry.COLUMN_NAME_SORT_VER + " INTEGER," +
                    PulseOxEntry.COLUMN_NAME_SORT_UPDATED_TIME + " LONG," +
                    PulseOxEntry.COLUMN_NAME_SORT_SYNCED_TIME + " LONG" +
                    ")";

    public static final String SQL_DELETE_ENTRIES =
            "DROP TABLE IF EXISTS " + PulseOxEntry.TABLE_NAME;

    public static final String SQL_ADD_ENTRY =
            "INSERT INTO " + PulseOxEntry.TABLE_NAME;

    public static final String SQL_EQ_BT_MAC =
            PulseOxEntry.COLUMN_NAME_BT_MAC + " = ?";

    public static final String SQL_EQ_SN =
            PulseOxEntry.COLUMN_NAME_SN + " = ?";

    public static final String SQL_CONTAINS_MODEL =
            PulseOxEntry.COLUMN_NAME_MODEL + " LIKE ?";

    public static final String[] ALL_COLS;

    static {
        ALL_COLS = new String[]{
                PulseOxEntry._ID,
                PulseOxEntry.COLUMN_NAME_SN,
                PulseOxEntry.COLUMN_NAME_BT_MAC,
                PulseOxEntry.COLUMN_NAME_DEVICE_ID,
                PulseOxEntry.COLUMN_NAME_MODEL,
                PulseOxEntry.COLUMN_NAME_PASS_KEY,
                PulseOxEntry.COLUMN_NAME_LAST_CONTACT,
                PulseOxEntry.COLUMN_NAME_LAST_STATUS,
                PulseOxEntry.COLUMN_NAME_BATTERY_LEVEL,
                PulseOxEntry.COLUMN_NAME_USER_ID,
                PulseOxEntry.COLUMN_NAME_USER_PIN,
                PulseOxEntry.COLUMN_NAME_PAIRED,
                PulseOxEntry.COLUMN_NAME_ROOT_URL,
                PulseOxEntry.COLUMN_NAME_TZ_OFFSET,
                PulseOxEntry.COLUMN_NAME_EXTRA_INFO_JSON,
                PulseOxEntry.COLUMN_NAME_OUTSTANDING_SYNC_COUNT,
                PulseOxEntry.COLUMN_NAME_SORT_VER,
                PulseOxEntry.COLUMN_NAME_SORT_UPDATED_TIME,
                PulseOxEntry.COLUMN_NAME_SORT_SYNCED_TIME
        };
    }
}
