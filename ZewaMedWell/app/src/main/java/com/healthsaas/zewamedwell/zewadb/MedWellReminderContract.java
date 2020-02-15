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

public final class MedWellReminderContract {
    private MedWellReminderContract() {}

    public static class MedWellReminderEntry implements BaseColumns {
        public static final String TABLE_NAME = "MedWellReminderTable";
        public static final String COLUMN_NAME_SN = "sn";
        public static final String COLUMN_NAME_SORT_VER = "sort_ver";
        public static final String COLUMN_NAME_SORT_UPDATED_TIME = "sort_updated_time";
        public static final String COLUMN_NAME_SORT_SYNCED_TIME = "sort_synced_time";
        public static final String COLUMN_NAME_ROOT_URL = "root_url";
        public static final String COLUMN_NAME_TZ_OFFSET = "tz_offset";
        public static final String COLUMN_NAME_EXTRA_INFO_JSON = "extra_info";
        public static final String COLUMN_NAME_WELL = "well";
        public static final String COLUMN_NAME_START_HR = "start_hr";
        public static final String COLUMN_NAME_START_MN = "start_mn";
        public static final String COLUMN_NAME_REPEAT_INTERVAL = "repeat_interval";
        public static final String COLUMN_NAME_ADVANCE_AFTER = "advance_after";
        public static final String COLUMN_NAME_BLINK_SPEED = "blink_speed";
        public static final String COLUMN_NAME_BEEP_INTERVAL = "beep_interval";
        public static final String COLUMN_NAME_BEEP_COUNT = "beep_count";
    }

    public static final String SQL_CREATE_ENTRIES =
            "CREATE TABLE " + MedWellReminderEntry.TABLE_NAME + " ("  +
                    MedWellReminderEntry._ID + " INTEGER PRIMARY KEY," +
                    MedWellReminderEntry.COLUMN_NAME_SORT_VER + " INTEGER," +
                    MedWellReminderEntry.COLUMN_NAME_SORT_UPDATED_TIME + " LONG," +
                    MedWellReminderEntry.COLUMN_NAME_SORT_SYNCED_TIME + " LONG," +
                    MedWellReminderEntry.COLUMN_NAME_ROOT_URL + " TEXT," +
                    MedWellReminderEntry.COLUMN_NAME_TZ_OFFSET + " TEXT," +
                    MedWellReminderEntry.COLUMN_NAME_EXTRA_INFO_JSON + " TEXT," +
                    MedWellReminderEntry.COLUMN_NAME_SN + " TEXT," +
                    MedWellReminderEntry.COLUMN_NAME_WELL + " INTEGER," +
                    MedWellReminderEntry.COLUMN_NAME_START_HR + " INTEGER," +
                    MedWellReminderEntry.COLUMN_NAME_START_MN + " INTEGER," +
                    MedWellReminderEntry.COLUMN_NAME_REPEAT_INTERVAL + " INTEGER," +
                    MedWellReminderEntry.COLUMN_NAME_ADVANCE_AFTER + " INTEGER," +
                    MedWellReminderEntry.COLUMN_NAME_BLINK_SPEED + " INTEGER," +
                    MedWellReminderEntry.COLUMN_NAME_BEEP_INTERVAL + " INTEGER," +
                    MedWellReminderEntry.COLUMN_NAME_BEEP_COUNT + " INTEGER" +
                    ")";

    public static final String SQL_DELETE_ENTRIES =
            "DROP TABLE IF EXISTS " + MedWellReminderEntry.TABLE_NAME;

    public static final String SQL_ADD_ENTRY =
            "INSERT INTO " + MedWellReminderEntry.TABLE_NAME ;

    public static final String SQL_EQ_SN =
            MedWellReminderEntry.COLUMN_NAME_SN + " = ?";

}
