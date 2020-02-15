package com.healthsaas.zewapulseox.zewadb;

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

import com.healthsaas.zewapulseox.zewa.dataModels.deviceObject;

import java.util.ArrayList;

/**
 * BT Device database management functions
 */

public class BtDeviceDb {
    private static final String LOG_TAG =  BtDeviceDb.class.getSimpleName();

    // cache application context
    private final Context mContext;
    private final BtDeviceDbHelper mDbHelper;
    private SQLiteDatabase mDb = null;

    /**
     * Constructor
     *   automatically opens writable database
     *
     * @param context  normally Application context
     */
    public BtDeviceDb(Context context) {
        mContext = context;
        mDbHelper = new BtDeviceDbHelper(context);
    }

    /**
     * Inserts new BT device
     *
     * @param devObj  BT device to insert
     * @return  BT device database row _ID
     */

    public synchronized long insert(final deviceObject devObj) {

        long rowId = findBySN(devObj.serialNumber);

        // insert if serialNumber not in database
        if ( rowId == -1 ) {
            Log.d(LOG_TAG, "insert new serialNumber: " + devObj.serialNumber);

            ContentValues row = new ContentValues();


            row.put(PulseOxContract.PulseOxEntry.COLUMN_NAME_SN, devObj.serialNumber);
            row.put(PulseOxContract.PulseOxEntry.COLUMN_NAME_BT_MAC, devObj.macAddress);
            row.put(PulseOxContract.PulseOxEntry.COLUMN_NAME_MODEL, devObj.modelName);
            row.put(PulseOxContract.PulseOxEntry.COLUMN_NAME_PASS_KEY, devObj.passKey);
            row.put(PulseOxContract.PulseOxEntry.COLUMN_NAME_LAST_CONTACT, devObj.lastContact);
            row.put(PulseOxContract.PulseOxEntry.COLUMN_NAME_LAST_STATUS, devObj.lastStatus);
            row.put(PulseOxContract.PulseOxEntry.COLUMN_NAME_BATTERY_LEVEL, devObj.batteryLevel);
            row.put(PulseOxContract.PulseOxEntry.COLUMN_NAME_USER_ID, devObj.userID);
            row.put(PulseOxContract.PulseOxEntry.COLUMN_NAME_USER_PIN, devObj.userPIN);
            row.put(PulseOxContract.PulseOxEntry.COLUMN_NAME_EXTRA_INFO_JSON, devObj.extraInfoJson);
            row.put(PulseOxContract.PulseOxEntry.COLUMN_NAME_TZ_OFFSET, devObj.timeZoneOffset);
            row.put(PulseOxContract.PulseOxEntry.COLUMN_NAME_OUTSTANDING_SYNC_COUNT, devObj.outstandingSyncCount);
            row.put(PulseOxContract.PulseOxEntry.COLUMN_NAME_SORT_VER, devObj.sortVer);
            row.put(PulseOxContract.PulseOxEntry.COLUMN_NAME_SORT_UPDATED_TIME, devObj.sortUpdatedTime);
            row.put(PulseOxContract.PulseOxEntry.COLUMN_NAME_SORT_SYNCED_TIME, devObj.sortSyncedTime);

            rowId = mDb.insert(PulseOxContract.PulseOxEntry.TABLE_NAME, null, row);
        }
        else {
            Log.d(LOG_TAG, "DB already has serialNumber: " + devObj.serialNumber);
        }

        return rowId;
    }

    /**
     * Update existing BT device
     *
     * @param devObj  BT device to update
     * @return  BT device database row _ID
     */

    public synchronized long update(final deviceObject devObj) {
        long rowId = -1;

        if (devObj == null) {
            Log.e(LOG_TAG, "devObj is null");
            return rowId;
        }

        ContentValues cols = new ContentValues();

        cols.put(PulseOxContract.PulseOxEntry.COLUMN_NAME_PASS_KEY, devObj.passKey);
        cols.put(PulseOxContract.PulseOxEntry.COLUMN_NAME_LAST_CONTACT, devObj.lastContact);
        cols.put(PulseOxContract.PulseOxEntry.COLUMN_NAME_LAST_STATUS, devObj.lastStatus);
        cols.put(PulseOxContract.PulseOxEntry.COLUMN_NAME_BATTERY_LEVEL, devObj.batteryLevel);
        cols.put(PulseOxContract.PulseOxEntry.COLUMN_NAME_USER_ID, devObj.userID);
        cols.put(PulseOxContract.PulseOxEntry.COLUMN_NAME_USER_PIN, devObj.userPIN);
        cols.put(PulseOxContract.PulseOxEntry.COLUMN_NAME_EXTRA_INFO_JSON, devObj.extraInfoJson);
        cols.put(PulseOxContract.PulseOxEntry.COLUMN_NAME_TZ_OFFSET, devObj.timeZoneOffset);
        cols.put(PulseOxContract.PulseOxEntry.COLUMN_NAME_OUTSTANDING_SYNC_COUNT, devObj.outstandingSyncCount);
        cols.put(PulseOxContract.PulseOxEntry.COLUMN_NAME_SORT_VER, devObj.sortVer);
        cols.put(PulseOxContract.PulseOxEntry.COLUMN_NAME_SORT_UPDATED_TIME, devObj.sortUpdatedTime);
        cols.put(PulseOxContract.PulseOxEntry.COLUMN_NAME_SORT_SYNCED_TIME, devObj.sortSyncedTime);

        return update(devObj.serialNumber, cols);
    }


    /**
     * Update existing BT device
     *
     * @param serialNumber  BT device to update
     * @param cols  database columns to update
     * @return  BT device database row _ID
     */

    public synchronized long update(final String serialNumber, final ContentValues cols) {
        long rowId = -1;

        if ( (serialNumber == null) || serialNumber.isEmpty()
                || (cols == null) || (cols.size() == 0) ) {
            Log.e(LOG_TAG, "empty serialNumber or cols");
            return rowId;
        }

        final String[] selectionArgs = {serialNumber};
        rowId = mDb.update(PulseOxContract.PulseOxEntry.TABLE_NAME, cols, PulseOxContract.SQL_EQ_SN, selectionArgs);

        if (rowId != -1) {
            Log.d(LOG_TAG, "updated serialNumber: " + serialNumber);
        } else {
            Log.e(LOG_TAG, "DB does not contain serialNumber: " + serialNumber);
        }

        return rowId;
    }
    
    /**
     *
     * @param bt_mac  MAC address of BT device
     * @return number of BT devices deleted or -1 if not found/error
     */
    public synchronized int deleteByMac(final String bt_mac) {
        String[] selectionArgs = { bt_mac };

        return mDb.delete(PulseOxContract.PulseOxEntry.TABLE_NAME,
                PulseOxContract.SQL_EQ_BT_MAC,
                selectionArgs);
    }

    /**
     * deletes BT device given serial number
     *
     * @param serialNumber
     * @return
     */
    public synchronized int deleteBySN(final String serialNumber) {
        String[] selectionArgs = { serialNumber };

        return mDb.delete(PulseOxContract.PulseOxEntry.TABLE_NAME,
                PulseOxContract.SQL_EQ_SN,
                selectionArgs);
    }

    /**
     * Insert deviceObject into given ArrayList using data from BT database
     *
     * @param allDevs will be updated with deviceObjects created from DB
     */
    public void getAllDevices(ArrayList<deviceObject> allDevs) {

        if (allDevs == null) {
            return;
        }

        Cursor cursor = mDb.query(PulseOxContract.PulseOxEntry.TABLE_NAME, PulseOxContract.ALL_COLS,
                null, null, null, null, null);

        while( cursor.moveToNext() ) {
            allDevs.add(getDevice(cursor));
        }
    }

    /**
     * create a deviceObject from BT DB with the specified serialNumber
     *
     * @param serialNumber BT device data to find in BT DB
     * @return
     */
    public deviceObject getDeviceBySN(final String serialNumber) {
        if( (mDb == null) || (serialNumber == null) || serialNumber.isEmpty()) {
            return null;
        }

        final String[] selectionArgs = { serialNumber };
        Cursor cursor = mDb.query(PulseOxContract.PulseOxEntry.TABLE_NAME,PulseOxContract.ALL_COLS,
                PulseOxContract.SQL_EQ_SN, selectionArgs,
                null, null, null, "1");

        if ( (cursor == null) || (cursor.getCount() == 0)) {
            return null;
        }
        cursor.moveToFirst();

        deviceObject dev = getDevice(cursor);

        return dev;
    }

    /**
     * create a deviceObject from the current row in BT DB
     *
     * @param cursor
     * @return
     */
    synchronized deviceObject getDevice(Cursor cursor) {
        deviceObject dev = new deviceObject();

        dev.deviceNumber = getIntColByName(cursor, PulseOxContract.PulseOxEntry._ID);
        dev.serialNumber = getStringColByName(cursor,PulseOxContract.PulseOxEntry.COLUMN_NAME_SN);
        dev.macAddress = getStringColByName(cursor, PulseOxContract.PulseOxEntry.COLUMN_NAME_BT_MAC);
        dev.deviceID = getStringColByName(cursor, PulseOxContract.PulseOxEntry.COLUMN_NAME_DEVICE_ID);
        dev.modelName = getStringColByName(cursor, PulseOxContract.PulseOxEntry.COLUMN_NAME_MODEL);
        dev.passKey = getStringColByName(cursor, PulseOxContract.PulseOxEntry.COLUMN_NAME_PASS_KEY);
        dev.lastContact = getStringColByName(cursor, PulseOxContract.PulseOxEntry.COLUMN_NAME_LAST_CONTACT);
        dev.lastStatus = getStringColByName(cursor, PulseOxContract.PulseOxEntry.COLUMN_NAME_LAST_STATUS);
        dev.batteryLevel = getIntColByName(cursor, PulseOxContract.PulseOxEntry.COLUMN_NAME_BATTERY_LEVEL);
        dev.userID = getStringColByName(cursor, PulseOxContract.PulseOxEntry.COLUMN_NAME_USER_ID);
        dev.userPIN = getStringColByName(cursor, PulseOxContract.PulseOxEntry.COLUMN_NAME_USER_PIN);
        dev.paired = getBoolColByName(cursor, PulseOxContract.PulseOxEntry.COLUMN_NAME_PAIRED);
        dev.rootURL = getStringColByName(cursor, PulseOxContract.PulseOxEntry.COLUMN_NAME_ROOT_URL);
        dev.timeZoneOffset = getStringColByName(cursor, PulseOxContract.PulseOxEntry.COLUMN_NAME_TZ_OFFSET);
        dev.extraInfoJson = getStringColByName(cursor, PulseOxContract.PulseOxEntry.COLUMN_NAME_EXTRA_INFO_JSON);
        dev.outstandingSyncCount = getIntColByName(cursor, PulseOxContract.PulseOxEntry.COLUMN_NAME_OUTSTANDING_SYNC_COUNT);
        dev.sortVer = getIntColByName(cursor, PulseOxContract.PulseOxEntry.COLUMN_NAME_SORT_VER);
        dev.sortUpdatedTime = getLongColByName(cursor, PulseOxContract.PulseOxEntry.COLUMN_NAME_SORT_UPDATED_TIME);
        dev.sortSyncedTime = getLongColByName(cursor, PulseOxContract.PulseOxEntry.COLUMN_NAME_SORT_SYNCED_TIME);

        return dev;
    }
    /**
     *
     * @param cursor SQLite row
     * @param colName  column name of value to return
     * @return  column String value
     */
    String getStringColByName(Cursor cursor, String colName) {
        return cursor.getString(cursor.getColumnIndex(colName));
    }

    /**
     *
     * @param cursor  SQLite row
     * @param colName  column name of value to return
     * @return  column int value
     */
    int getIntColByName(Cursor cursor, String colName) {
        int col = cursor.getColumnIndex(colName);
        return cursor.getInt(col);
    }

    /**
     *
     * @param cursor  SQLite row
     * @param colName  column name of value to return
     * @return  column long value
     */
    long getLongColByName(Cursor cursor, String colName) {
        return cursor.getLong(cursor.getColumnIndex(colName));
    }

    /**
     *
     * @param cursor  SQLite row
     * @param colName  column name of value to return
     * @return column boolean value
     */
    boolean getBoolColByName(Cursor cursor, String colName) {
        return  (cursor.getInt(cursor.getColumnIndex(colName)) == 0)?
            false : true ;
    }

    /**
     * lookup by serial Number for BT MAC address
     *
     * @param serialNumber
     * @return  String BT MAC address
     */
    public String getBtMacForSN(final String serialNumber) {
        final String[] columns = { PulseOxContract.PulseOxEntry.COLUMN_NAME_BT_MAC};
        final String[] selectionArgs = { serialNumber };

        Cursor cursor = mDb.query(PulseOxContract.PulseOxEntry.TABLE_NAME, columns,
                PulseOxContract.SQL_EQ_SN, selectionArgs, null, null, null, "1");

        String bt_mac = null;
        // if BT_MAC found
        if ( cursor != null ) {

            if ( cursor.moveToFirst() ) {
                // return bt_mac in column 0 of result set
                bt_mac = cursor.getString(0);
            }

            // free resources
            cursor.close();
        }

        return bt_mac;
    }


    /**
     * lookup by BT MAC address for serial number
     *
     * @param bt_mac
     * @return  String serial number
     */
    public String getSNforBtMac(final String bt_mac) {
        final String[] columns = { PulseOxContract.PulseOxEntry.COLUMN_NAME_SN};
        final String[] selectionArgs = { bt_mac };

        Cursor cursor = mDb.query(PulseOxContract.PulseOxEntry.TABLE_NAME, columns,
                PulseOxContract.SQL_EQ_BT_MAC, selectionArgs, null, null, null, "1");

        String serialNumber = null;
        // if BT_MAC found
        if ( cursor != null ) {

            if ( cursor.moveToFirst() ) {
                // return serial number in column 0 of result set
                serialNumber = cursor.getString(0);
            }

            // free resources
            cursor.close();
        }

        return serialNumber;
    }

    /**
     *
     * @param bt_mac  MAC address of BT device
     * @return  BT device database row _ID
     */
    public int findByMac(final String bt_mac) {
        final String[] columns = { PulseOxContract.PulseOxEntry._ID };
        final String[] selectionArgs = { bt_mac };

        Cursor cursor = mDb.query(PulseOxContract.PulseOxEntry.TABLE_NAME, columns,
                PulseOxContract.SQL_EQ_BT_MAC, selectionArgs, null, null, null, "1");

        int rowId = -1;
        // if BT_MAC found
        if ( cursor != null ) {

            if ( cursor.moveToFirst() ) {
                // return row _ID which is column index 0
                rowId = cursor.getInt(0);
            }
            // free resources
            cursor.close();
        }
        return rowId;
    }

    /**
     *
     * @param serialNumber  serial number
     * @return BT device database row _ID
     */
    public int findBySN(final String serialNumber) {
        final String[] columns = { PulseOxContract.PulseOxEntry._ID };
        final String[] selectionArgs = { serialNumber };

        Cursor cursor = mDb.query(PulseOxContract.PulseOxEntry.TABLE_NAME, columns,
                PulseOxContract.SQL_EQ_SN, selectionArgs, null, null, null, "1");

        int rowId = -1;
        // if serial found found
        if ( cursor != null ) {

            if ( cursor.moveToFirst() ) {
                // return row _ID which is column index 0
                rowId = cursor.getInt(0);
            }

            // free resources
            cursor.close();
        }

        return rowId;
    }

    /**
     *
     * @return number of BT devices
     */
    public int getNumRows() {
        final String[] columns = { PulseOxContract.PulseOxEntry._ID };
        Cursor cursor = mDb.query(PulseOxContract.PulseOxEntry.TABLE_NAME, columns,
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
    public synchronized BtDeviceDb open() throws SQLException {
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
            bSuccessful = mContext.deleteDatabase(BtDeviceDbHelper.DATABASE_NAME);
            if ( bSuccessful ) {
                Log.d(LOG_TAG, "Successfully deleted: " + BtDeviceDbHelper.DATABASE_NAME);
            }
            else {
                Log.e(LOG_TAG, "Failed to delete: " + BtDeviceDbHelper.DATABASE_NAME);
            }
        }
        return bSuccessful;
    }

    /**
     * Delete BT DB file
     *
     * @param context
     * @return
     */
    public static synchronized boolean deleteDbFile(Context context) {
        boolean bSuccessful = false;

        if (context != null) {
            bSuccessful = context.deleteDatabase(BtDeviceDbHelper.DATABASE_NAME);
            if ( bSuccessful ) {
                Log.d(LOG_TAG, "Successfully deleted: " + BtDeviceDbHelper.DATABASE_NAME);
            }
            else {
                Log.e(LOG_TAG, "Failed to delete: " + BtDeviceDbHelper.DATABASE_NAME);
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
