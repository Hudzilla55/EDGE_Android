package com.healthsaas.edge.safe.SafeDb;

import android.provider.BaseColumns;

public final class SafeDbContract {
    private SafeDbContract() {
    }

    static class SafeDbEntry implements BaseColumns {
        static final String TABLE_NAME = "SafeDbPayloadTable";
        static final String COLUMN_NAME_URL = "url";
        static final String COLUMN_NAME_HEADERS = "headers";
        static final String COLUMN_NAME_PAYLOAD = "manufacture";
        static final String COLUMN_NAME_SENDING = "receipt_time";
        static final String COLUMN_NAME_TZ_OFFSET = "tz_offset";
    }

    static final String SQL_CREATE_ENTRIES =
            "CREATE TABLE " + SafeDbEntry.TABLE_NAME + " (" +
                    SafeDbEntry._ID + " INTEGER PRIMARY KEY," +
                    SafeDbEntry.COLUMN_NAME_URL + " TEXT," +
                    SafeDbEntry.COLUMN_NAME_HEADERS + " TEXT," +
                    SafeDbEntry.COLUMN_NAME_PAYLOAD + " TEXT," +
                    SafeDbEntry.COLUMN_NAME_SENDING + " LONG" +
                    ")";

    static final String SQL_DELETE_ENTRIES =
            "DROP TABLE IF EXISTS " + SafeDbEntry.TABLE_NAME;

    static final String SQL_ADD_ENTRY =
            "INSERT INTO " + SafeDbEntry.TABLE_NAME;

    static final String SQL_DELETE_ENTRY =
            "DELETE FROM " + SafeDbEntry.TABLE_NAME + " WHERE " + SafeDbEntry._ID + " = ?";

    static final String SQL_EQ_ID =
            SafeDbEntry._ID + " = ?";

    static final String SQL_CONTAINS_MODEL =
            SafeDbEntry.COLUMN_NAME_URL + " LIKE ?";

    static final String[] ALL_COLS;

    static {
        ALL_COLS = new String[]{
                SafeDbEntry._ID,
                SafeDbEntry.COLUMN_NAME_URL,
                SafeDbEntry.COLUMN_NAME_HEADERS,
                SafeDbEntry.COLUMN_NAME_PAYLOAD,
                SafeDbEntry.COLUMN_NAME_SENDING
        };
    }
}
