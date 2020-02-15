package com.healthsaas.zewamedwell.zewadb;

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
 * define BLE MedWell Device database schema
 */

public final class MedWellContract {
    private MedWellContract() {
    }

    public static class MedWellEntry implements BaseColumns {
        public static final String TABLE_NAME = "MedWellTable";
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
            "CREATE TABLE " + MedWellEntry.TABLE_NAME + " (" +
                    MedWellEntry._ID + " INTEGER PRIMARY KEY," +
                    MedWellEntry.COLUMN_NAME_SN + " TEXT," +
                    MedWellEntry.COLUMN_NAME_BT_MAC + " TEXT," +
                    MedWellEntry.COLUMN_NAME_DEVICE_ID + " TEXT," +
                    MedWellEntry.COLUMN_NAME_MODEL + " TEXT," +
                    MedWellEntry.COLUMN_NAME_PASS_KEY + " TEXT," +
                    MedWellEntry.COLUMN_NAME_LAST_CONTACT + " LONG," +
                    MedWellEntry.COLUMN_NAME_LAST_STATUS + " TEXT," +
                    MedWellEntry.COLUMN_NAME_BATTERY_LEVEL + " INTEGER," +
                    MedWellEntry.COLUMN_NAME_USER_ID + " TEXT," +
                    MedWellEntry.COLUMN_NAME_USER_PIN + " TEXT," +
                    MedWellEntry.COLUMN_NAME_PAIRED + " BOOLEAN," +
                    MedWellEntry.COLUMN_NAME_ROOT_URL + " TEXT," +
                    MedWellEntry.COLUMN_NAME_TZ_OFFSET + " TEXT," +
                    MedWellEntry.COLUMN_NAME_EXTRA_INFO_JSON + " TEXT," +
                    MedWellEntry.COLUMN_NAME_OUTSTANDING_SYNC_COUNT + " INTEGER," +
                    MedWellEntry.COLUMN_NAME_SORT_VER + " INTEGER," +
                    MedWellEntry.COLUMN_NAME_SORT_UPDATED_TIME + " LONG," +
                    MedWellEntry.COLUMN_NAME_SORT_SYNCED_TIME + " LONG" +
                    ")";

    public static final String SQL_DELETE_ENTRIES =
            "DROP TABLE IF EXISTS " + MedWellEntry.TABLE_NAME;

    public static final String SQL_ADD_ENTRY =
            "INSERT INTO " + MedWellEntry.TABLE_NAME;

    public static final String SQL_EQ_BT_MAC =
            MedWellEntry.COLUMN_NAME_BT_MAC + " = ?";

    public static final String SQL_EQ_SN =
            MedWellEntry.COLUMN_NAME_SN + " = ?";

    public static final String SQL_CONTAINS_MODEL =
            MedWellEntry.COLUMN_NAME_MODEL + " LIKE ?";

    public static final String[] ALL_COLS;

    static {
        ALL_COLS = new String[]{
                MedWellEntry._ID,
                MedWellEntry.COLUMN_NAME_SN,
                MedWellEntry.COLUMN_NAME_BT_MAC,
                MedWellEntry.COLUMN_NAME_DEVICE_ID,
                MedWellEntry.COLUMN_NAME_MODEL,
                MedWellEntry.COLUMN_NAME_PASS_KEY,
                MedWellEntry.COLUMN_NAME_LAST_CONTACT,
                MedWellEntry.COLUMN_NAME_LAST_STATUS,
                MedWellEntry.COLUMN_NAME_BATTERY_LEVEL,
                MedWellEntry.COLUMN_NAME_USER_ID,
                MedWellEntry.COLUMN_NAME_USER_PIN,
                MedWellEntry.COLUMN_NAME_PAIRED,
                MedWellEntry.COLUMN_NAME_ROOT_URL,
                MedWellEntry.COLUMN_NAME_TZ_OFFSET,
                MedWellEntry.COLUMN_NAME_EXTRA_INFO_JSON,
                MedWellEntry.COLUMN_NAME_OUTSTANDING_SYNC_COUNT,
                MedWellEntry.COLUMN_NAME_SORT_VER,
                MedWellEntry.COLUMN_NAME_SORT_UPDATED_TIME,
                MedWellEntry.COLUMN_NAME_SORT_SYNCED_TIME
        };
    }
}
