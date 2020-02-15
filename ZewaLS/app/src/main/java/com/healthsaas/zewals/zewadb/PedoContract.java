package com.healthsaas.zewals.zewadb;

import android.provider.BaseColumns;

public final class PedoContract {
    private PedoContract() {
    }

    static class PedoDbEntry implements BaseColumns {
        static final String TABLE_NAME = "PedometerEventTable";
        static final String COLUMN_NAME_MODEL = "model";
        static final String COLUMN_NAME_DEVICE_ID = "dev_id";
        static final String COLUMN_NAME_MANUFACTURE = "manufacture";
        static final String COLUMN_NAME_BATTERY_LEVEL = "battery_level";
        static final String COLUMN_NAME_WALK_STEPS = "walk_steps";
        static final String COLUMN_NAME_RUN_STEPS = "run_steps";
        static final String COLUMN_NAME_DISTANCE = "distance";
        static final String COLUMN_NAME_EXERCISE_TIME = "exercise_time";
        static final String COLUMN_NAME_CALORIES = "calories";
        static final String COLUMN_NAME_INTENSITY_LEVEL = "intensity_level";
        static final String COLUMN_NAME_SLEEP_STATUS = "sleep_status";
        static final String COLUMN_NAME_EXERCISE_AMOUNT = "exercise_amount";
        static final String COLUMN_NAME_MEASUREMENT_TIME = "measurement_time";
        static final String COLUMN_NAME_RECEIPT_TIME = "receipt_time";
        static final String COLUMN_NAME_TZ_OFFSET = "tz_offset";
    }

    static final String SQL_CREATE_ENTRIES =
            "CREATE TABLE " + PedoDbEntry.TABLE_NAME + " (" +
                    PedoDbEntry._ID + " INTEGER PRIMARY KEY," +
                    PedoDbEntry.COLUMN_NAME_MODEL + " TEXT," +
                    PedoDbEntry.COLUMN_NAME_DEVICE_ID + " TEXT," +
                    PedoDbEntry.COLUMN_NAME_MANUFACTURE + " TEXT," +
                    PedoDbEntry.COLUMN_NAME_BATTERY_LEVEL + " INTEGER," +
                    PedoDbEntry.COLUMN_NAME_WALK_STEPS + " INTEGER," +
                    PedoDbEntry.COLUMN_NAME_RUN_STEPS + " INTEGER," +
                    PedoDbEntry.COLUMN_NAME_DISTANCE + " INTEGER," +
                    PedoDbEntry.COLUMN_NAME_EXERCISE_TIME + " INTEGER," +
                    PedoDbEntry.COLUMN_NAME_CALORIES + " FLOAT," +
                    PedoDbEntry.COLUMN_NAME_INTENSITY_LEVEL + " INTEGER," +
                    PedoDbEntry.COLUMN_NAME_SLEEP_STATUS + " INTEGER," +
                    PedoDbEntry.COLUMN_NAME_EXERCISE_AMOUNT + " INTEGER," +
                    PedoDbEntry.COLUMN_NAME_MEASUREMENT_TIME + " LONG," +
                    PedoDbEntry.COLUMN_NAME_RECEIPT_TIME + " LONG," +
                    PedoDbEntry.COLUMN_NAME_TZ_OFFSET + " INTEGER" +
                    ")";

    static final String SQL_DELETE_ENTRIES =
            "DROP TABLE IF EXISTS " + PedoDbEntry.TABLE_NAME;

    static final String SQL_ADD_ENTRY =
            "INSERT INTO " + PedoDbEntry.TABLE_NAME;

    static final String SQL_DELETE_ENTRY =
            "DELETE FROM " + PedoDbEntry.TABLE_NAME + " WHERE " + PedoDbEntry._ID + " = ?";

    static final String SQL_EQ_ID =
            PedoDbEntry._ID + " = ?";

    static final String SQL_CONTAINS_MODEL =
            PedoDbEntry.COLUMN_NAME_MODEL + " LIKE ?";

    static final String[] ALL_COLS;

    static {
        ALL_COLS = new String[]{
                PedoDbEntry._ID,
                PedoDbEntry.COLUMN_NAME_MODEL,
                PedoDbEntry.COLUMN_NAME_DEVICE_ID,
                PedoDbEntry.COLUMN_NAME_MANUFACTURE,
                PedoDbEntry.COLUMN_NAME_BATTERY_LEVEL,
                PedoDbEntry.COLUMN_NAME_WALK_STEPS,
                PedoDbEntry.COLUMN_NAME_RUN_STEPS,
                PedoDbEntry.COLUMN_NAME_DISTANCE,
                PedoDbEntry.COLUMN_NAME_EXERCISE_TIME,
                PedoDbEntry.COLUMN_NAME_CALORIES,
                PedoDbEntry.COLUMN_NAME_INTENSITY_LEVEL,
                PedoDbEntry.COLUMN_NAME_SLEEP_STATUS,
                PedoDbEntry.COLUMN_NAME_EXERCISE_AMOUNT,
                PedoDbEntry.COLUMN_NAME_MEASUREMENT_TIME,
                PedoDbEntry.COLUMN_NAME_RECEIPT_TIME,
                PedoDbEntry.COLUMN_NAME_TZ_OFFSET
        };
    }
}
