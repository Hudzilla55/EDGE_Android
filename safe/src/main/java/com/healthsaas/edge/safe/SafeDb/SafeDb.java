package com.healthsaas.edge.safe.SafeDb;

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

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.SQLException;
import android.database.sqlite.SQLiteDatabase;
import android.util.Log;

import java.util.ArrayList;

/**
 * BT Device database management functions
 */

public class SafeDb {
    private static final String LOG_TAG =  SafeDb.class.getSimpleName();

    // cache application context
    private final Context mContext;
    private final SafeDbHelper mDbHelper;
    private SQLiteDatabase mDb = null;

    /**
     * Constructor
     *   automatically opens writable database
     *
     * @param context  normally Application context
     */
    public SafeDb(Context context) {
        mContext = context;
        mDbHelper = new SafeDbHelper(context);
    }

    /**
     * Inserts new Payload Entry
     *
     * @param safeDbEntry  device data payload to insert
     * @return  device database row _ID
     */

    public synchronized long insert(final SafeDbEntry safeDbEntry) {

        long rowId;

        ContentValues row = new ContentValues();

        row.put(SafeDbContract.SafeDbEntry.COLUMN_NAME_URL, safeDbEntry.url);
        row.put(SafeDbContract.SafeDbEntry.COLUMN_NAME_HEADERS, safeDbEntry.headers);
        row.put(SafeDbContract.SafeDbEntry.COLUMN_NAME_PAYLOAD, safeDbEntry.payload);
        row.put(SafeDbContract.SafeDbEntry.COLUMN_NAME_SENDING, safeDbEntry.sending);

        rowId = mDb.insert(SafeDbContract.SafeDbEntry.TABLE_NAME, null, row);
        return rowId;
    }

    /**
     * deletes SafeDbEntry given _ID
     *
     * @param _ID database id for item to delete
     * @return
     */
    public synchronized int deleteByID(final long _ID) {
        String[] selectionArgs = { String.valueOf(_ID) };

        return mDb.delete(SafeDbContract.SafeDbEntry.TABLE_NAME,
                SafeDbContract.SQL_EQ_ID,
                selectionArgs);
    }

    public synchronized int deleteByIDS(final ArrayList<Long> IDS) {
         int resp = 0;
        for (Long id : IDS) {
            resp += deleteByID(id);
        }
        return resp;
    }

    public synchronized int updateSending(final long _ID, long state) {
        String[] selectionArgs = { String.valueOf(_ID) };
        ContentValues cv = new ContentValues();
        cv.put(SafeDbContract.SafeDbEntry.COLUMN_NAME_SENDING, state);
        return mDb.update(SafeDbContract.SafeDbEntry.TABLE_NAME,
                cv,
                SafeDbContract.SQL_EQ_ID,
                selectionArgs);
    }

    /**
     * Insert SafeDbEntry into given ArrayList using data from Safe database
     *
     * @param allEntries will be updated with SafeDbEntry's created from DB
     */
    public synchronized void getAllEntries(ArrayList<SafeDbEntry> allEntries, boolean skipSending) {

        if (allEntries == null) {
            return;
        }
        Cursor cursor;
        if (skipSending) {
            String[] selectionArgs = { String.valueOf(0) };

            cursor = mDb.query(SafeDbContract.SafeDbEntry.TABLE_NAME, SafeDbContract.ALL_COLS,
                    SafeDbContract.SafeDbEntry.COLUMN_NAME_SENDING + " = ?",
                    selectionArgs,
                    null,
                    null,
                    "_ID");

        } else {
            cursor = mDb.query(SafeDbContract.SafeDbEntry.TABLE_NAME, SafeDbContract.ALL_COLS,
                    null,
                    null,
                    null,
                    null,
                    SafeDbContract.SQL_EQ_ID);
        }

        while( cursor.moveToNext() ) {
            allEntries.add(getEntry(cursor));
        }
        cursor.close();
    }

    /**
     * create a deviceObject from the current row in BT DB
     *
     * @param cursor
     * @return
     */
    synchronized SafeDbEntry getEntry(Cursor cursor) {
        SafeDbEntry safeDbEntry = new SafeDbEntry();

        safeDbEntry.ID = getIntColByName(cursor, SafeDbContract.SafeDbEntry._ID);
        safeDbEntry.url = getStringColByName(cursor, SafeDbContract.SafeDbEntry.COLUMN_NAME_URL);
        safeDbEntry.headers = getStringColByName(cursor, SafeDbContract.SafeDbEntry.COLUMN_NAME_HEADERS);
        safeDbEntry.payload = getStringColByName(cursor, SafeDbContract.SafeDbEntry.COLUMN_NAME_PAYLOAD);
        safeDbEntry.sending = getLongColByName(cursor, SafeDbContract.SafeDbEntry.COLUMN_NAME_SENDING);

        return safeDbEntry;
    }
    /**
     *
     * @param cursor SQLite row
     * @param colName  column name of value to return
     * @return  column String value
     */
    private String getStringColByName(Cursor cursor, String colName) {
        return cursor.getString(cursor.getColumnIndex(colName));
    }

    /**
     *
     * @param cursor  SQLite row
     * @param colName  column name of value to return
     * @return  column int value
     */
    private int getIntColByName(Cursor cursor, String colName) {
        int col = cursor.getColumnIndex(colName);
        return cursor.getInt(col);
    }

    /**
     *
     * @param cursor  SQLite row
     * @param colName  column name of value to return
     * @return  column long value
     */
    private float getFloatColByName(Cursor cursor, String colName) {
        return cursor.getFloat(cursor.getColumnIndex(colName));
    }

    /**
     *
     * @param cursor  SQLite row
     * @param colName  column name of value to return
     * @return  column long value
     */
    private long getLongColByName(Cursor cursor, String colName) {
        return cursor.getLong(cursor.getColumnIndex(colName));
    }

    /**
     *
     * @param cursor  SQLite row
     * @param colName  column name of value to return
     * @return column boolean value
     */
    private boolean getBoolColByName(Cursor cursor, String colName) {
        return  (cursor.getInt(cursor.getColumnIndex(colName)) == 0)?
            false : true ;
    }

    /**
     *
     * @return number of BT devices
     */
    public int getNumRows() {
        final String[] columns = { SafeDbContract.SafeDbEntry._ID };
        Cursor cursor = mDb.query(SafeDbContract.SafeDbEntry.TABLE_NAME, columns,
                null, null, null, null, null, null);

        int numRows = 0;
        if (cursor != null) {
            numRows = cursor.getCount();
            cursor.close();
        }
        return numRows;
    }

    /**
     * open the DB with write/read access or
     * just read access if that is all that is possible.
     *
     * @return this  BT device database adapter
     * @throws SQLException
     */
    public synchronized SafeDb open() throws SQLException {
        try {
            mDb = mDbHelper.getWritableDatabase();
            Log.d(LOG_TAG, "open().getWritableDatabase()");
        } catch (SQLException ex) {
            mDb = mDbHelper.getReadableDatabase();
            Log.d(LOG_TAG, "open().getReadableDatabase()");
        }
        return this;
    }

    /**
     * close the DB.
     */
    public synchronized void close() {
        Log.d(LOG_TAG, "close()");
        getDB().close();
    }

    /**
     * deletes BT Device database file
     */
    public synchronized boolean deleteDbFile() {
        boolean bSuccessful = false;

        if (mContext != null) {
            if ( mDbHelper != null ) {
                mDbHelper.close();
            }
            bSuccessful = mContext.deleteDatabase(SafeDbHelper.DATABASE_NAME);
            if ( bSuccessful ) {
                Log.d(LOG_TAG, "Successfully deleted: " + SafeDbHelper.DATABASE_NAME);
            }
            else {
                Log.e(LOG_TAG, "Failed to delete: " + SafeDbHelper.DATABASE_NAME);
            }
        }
        return bSuccessful;
    }

    /**
     * Delete DB file
     *
     * @param context
     * @return
     */
    public static synchronized boolean deleteDbFile(Context context) {
        boolean bSuccessful = false;

        if (context != null) {
            bSuccessful = context.deleteDatabase(SafeDbHelper.DATABASE_NAME);
            if ( bSuccessful ) {
                Log.d(LOG_TAG, "Successfully deleted: " + SafeDbHelper.DATABASE_NAME);
            }
            else {
                Log.e(LOG_TAG, "Failed to delete: " + SafeDbHelper.DATABASE_NAME);
            }
        }
        return bSuccessful;
    }

    /**
     * Get the underlying Database.
     *
     * @return
     */
    public SQLiteDatabase getDB() {
        return mDb;
    }

}
