package com.healthsaas.zewals.zewadb;

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

import java.security.InvalidAlgorithmParameterException;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;

import com.healthsaas.edge.safe.crypto.AES;

import javax.crypto.BadPaddingException;
import javax.crypto.IllegalBlockSizeException;
import javax.crypto.NoSuchPaddingException;

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
     * Inserts new Pedometer Entry
     *
     * @param pedoEntry  BT device to insert
     * @return  BT device database row _ID
     */

    public synchronized long insert(final PedoEntry pedoEntry) {

        long rowId;

        ContentValues row = new ContentValues();

        row.put(PedoContract.PedoDbEntry.COLUMN_NAME_DEVICE_ID, pedoEntry.deviceID);
        row.put(PedoContract.PedoDbEntry.COLUMN_NAME_MODEL, pedoEntry.modelName);
        row.put(PedoContract.PedoDbEntry.COLUMN_NAME_MANUFACTURE, pedoEntry.manufacturer);
        row.put(PedoContract.PedoDbEntry.COLUMN_NAME_BATTERY_LEVEL, pedoEntry.batteryLevel);
        row.put(PedoContract.PedoDbEntry.COLUMN_NAME_WALK_STEPS, pedoEntry.walkSteps);
        row.put(PedoContract.PedoDbEntry.COLUMN_NAME_RUN_STEPS, pedoEntry.runSteps);
        row.put(PedoContract.PedoDbEntry.COLUMN_NAME_DISTANCE, pedoEntry.distance);
        row.put(PedoContract.PedoDbEntry.COLUMN_NAME_EXERCISE_TIME, pedoEntry.exerciseTime);
        row.put(PedoContract.PedoDbEntry.COLUMN_NAME_CALORIES, pedoEntry.calories);
        row.put(PedoContract.PedoDbEntry.COLUMN_NAME_INTENSITY_LEVEL, pedoEntry.intensityLevel);
        row.put(PedoContract.PedoDbEntry.COLUMN_NAME_SLEEP_STATUS, pedoEntry.sleepStatus);
        row.put(PedoContract.PedoDbEntry.COLUMN_NAME_EXERCISE_AMOUNT, pedoEntry.exerciseAmount);
        row.put(PedoContract.PedoDbEntry.COLUMN_NAME_MEASUREMENT_TIME, pedoEntry.measurementTime);
        row.put(PedoContract.PedoDbEntry.COLUMN_NAME_RECEIPT_TIME, pedoEntry.receiptTime);
        row.put(PedoContract.PedoDbEntry.COLUMN_NAME_TZ_OFFSET, pedoEntry.measureTimeUtcOffset);

        rowId = mDb.insert(PedoContract.PedoDbEntry.TABLE_NAME, null, row);
        return rowId;
    }

    /**
     * deletes PedoEntry given _ID
     *
     * @param _ID database id for item to delete
     * @return
     */
     private synchronized int deleteByID(final int _ID) {
        String[] selectionArgs = { String.valueOf(_ID) };

        return mDb.delete(PedoContract.PedoDbEntry.TABLE_NAME,
                PedoContract.SQL_EQ_ID,
                selectionArgs);
    }

    public synchronized int deleteByIDS(final ArrayList<Integer> IDS) {
         int resp = 0;
        for (int id : IDS) {
            resp += deleteByID(id);
        }
        return resp;
    }

    /**
     * Insert PedoEntry into given ArrayList using data from Safe database
     *
     * @param allEntries will be updated with PedoEntry's created from DB
     */
    public void getAllEntries(ArrayList<PedoEntry> allEntries) {

        if (allEntries == null) {
            return;
        }

        Cursor cursor = mDb.query(PedoContract.PedoDbEntry.TABLE_NAME, PedoContract.ALL_COLS,
                null,
                null,
                null,
                null,
                PedoContract.PedoDbEntry.COLUMN_NAME_DEVICE_ID + ", " + PedoContract.PedoDbEntry.COLUMN_NAME_MEASUREMENT_TIME);

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
    synchronized PedoEntry getEntry(Cursor cursor) {
        PedoEntry pedoEntry = new PedoEntry();

        pedoEntry.ID = getIntColByName(cursor, PedoContract.PedoDbEntry._ID);
        pedoEntry.deviceID = getStringColByName(cursor, PedoContract.PedoDbEntry.COLUMN_NAME_DEVICE_ID);
        pedoEntry.modelName = getStringColByName(cursor, PedoContract.PedoDbEntry.COLUMN_NAME_MODEL);
        pedoEntry.manufacturer = getStringColByName(cursor, PedoContract.PedoDbEntry.COLUMN_NAME_MANUFACTURE);
        pedoEntry.batteryLevel = getIntColByName(cursor, PedoContract.PedoDbEntry.COLUMN_NAME_BATTERY_LEVEL);
        pedoEntry.walkSteps = getIntColByName(cursor, PedoContract.PedoDbEntry.COLUMN_NAME_WALK_STEPS);
        pedoEntry.runSteps = getIntColByName(cursor, PedoContract.PedoDbEntry.COLUMN_NAME_RUN_STEPS);
        pedoEntry.distance = getIntColByName(cursor, PedoContract.PedoDbEntry.COLUMN_NAME_DISTANCE);
        pedoEntry.exerciseTime = getIntColByName(cursor, PedoContract.PedoDbEntry.COLUMN_NAME_EXERCISE_TIME);
        pedoEntry.calories = getFloatColByName(cursor, PedoContract.PedoDbEntry.COLUMN_NAME_CALORIES);
        pedoEntry.intensityLevel = getIntColByName(cursor, PedoContract.PedoDbEntry.COLUMN_NAME_INTENSITY_LEVEL);
        pedoEntry.sleepStatus = getIntColByName(cursor, PedoContract.PedoDbEntry.COLUMN_NAME_SLEEP_STATUS);
        pedoEntry.exerciseAmount = getIntColByName(cursor, PedoContract.PedoDbEntry.COLUMN_NAME_EXERCISE_AMOUNT);
        pedoEntry.measurementTime = getLongColByName(cursor, PedoContract.PedoDbEntry.COLUMN_NAME_MEASUREMENT_TIME);
        pedoEntry.receiptTime = getLongColByName(cursor, PedoContract.PedoDbEntry.COLUMN_NAME_RECEIPT_TIME);
        pedoEntry.measureTimeUtcOffset = getIntColByName(cursor, PedoContract.PedoDbEntry.COLUMN_NAME_TZ_OFFSET);

        return pedoEntry;
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
        final String[] columns = { PedoContract.PedoDbEntry._ID };
        Cursor cursor = mDb.query(PedoContract.PedoDbEntry.TABLE_NAME, columns,
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
        Log.d(LOG_TAG, "open()");
        try {
            mDb = mDbHelper.getWritableDatabase();
        } catch (SQLException ex) {
            mDb = mDbHelper.getReadableDatabase();
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
