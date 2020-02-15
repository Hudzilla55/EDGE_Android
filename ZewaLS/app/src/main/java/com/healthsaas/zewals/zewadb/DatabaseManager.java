package com.healthsaas.zewals.zewadb;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.text.TextUtils;

import com.healthsaas.zewals.zewadb.SleepDataReaderContract.SleepDataEntry;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class DatabaseManager {

	private static SimpleDateFormat dateFormat = new SimpleDateFormat("HH:mm:ss yyyy/MM/dd", Locale.getDefault());


	/**
	 * Save sleep data
	 * @return
	 */
	public static int saveSleepData(Context appContext, SleepData sleepData){
		if(sleepData==null){
			return 0;
		}
		String deviceMac=sleepData.getDeviceMac().replace(":","").toUpperCase();
		SleepDataReaderHelper mDbHelper = new SleepDataReaderHelper(appContext);

		// Gets the data repository in write mode
		SQLiteDatabase db = mDbHelper.getWritableDatabase();
		long newRowId=0;
		// Create a new map of values, where column names are the keys
		ContentValues values = new ContentValues();
		values.put(SleepDataEntry.COLUMN_NAME_MAC,deviceMac);
		values.put(SleepDataEntry.COLUMN_NAME_UTC, sleepData.getUtc());
		values.put(SleepDataEntry.COLUMN_NAME_TIME_OFFSET, sleepData.getTimeOffset());
		values.put(SleepDataEntry.COLUMN_NAME_SRC_DATA, sleepData.getSrcData());

		// Insert the new row, returning the primary key value of the new row
		newRowId= db.insert(SleepDataEntry.TABLE_NAME, null, values);
		return (int)newRowId;
	}


	/**
	 * Query Sleep Data With Device's MAC
	 * @return
	 */
	public static List<SleepData> getSleepDataWithMac(Context appContext,String deviceMac){
		if(TextUtils.isEmpty(deviceMac) || appContext == null){
			return null;
		}
		deviceMac=deviceMac.replace(":","").toUpperCase();

		SleepDataReaderHelper mDbHelper = new SleepDataReaderHelper(appContext);
		SQLiteDatabase db = mDbHelper.getReadableDatabase();

		// Define a projection that specifies which columns from the database
		// you will actually use after this query.
		String[] projection = null;//get all

		// Filter results WHERE "title" = 'My Title'
		String selection = SleepDataEntry.COLUMN_NAME_MAC + " = ? ";
		String[] selectionArgs = { deviceMac };
		// How you want the results sorted in the resulting Cursor
		String sortOrder =
				SleepDataEntry.COLUMN_NAME_UTC + " DESC";
		Cursor cursor = db.query(
				SleepDataEntry.TABLE_NAME, // The table to query
				projection,             // The array of columns to return (pass null to get all)
				selection,              // The columns for the WHERE clause
				selectionArgs,          // The values for the WHERE clause
				null,                   // don't group the rows
				null,                   // don't filter by row groups
				sortOrder               // The sort order
				);
		if(cursor==null){
			return null;
		}
		List<SleepData> items = new ArrayList<SleepData>();
		while(cursor.moveToNext()) 
		{
			int macIndex=cursor.getColumnIndexOrThrow(SleepDataEntry.COLUMN_NAME_MAC);
			int utcIndex=cursor.getColumnIndexOrThrow(SleepDataEntry.COLUMN_NAME_UTC);
			int timeOffsetIndex=cursor.getColumnIndexOrThrow(SleepDataEntry.COLUMN_NAME_TIME_OFFSET);
			int srcDataIndex=cursor.getColumnIndexOrThrow(SleepDataEntry.COLUMN_NAME_SRC_DATA);

			SleepData data=new SleepData();
			data.setDeviceMac(cursor.getString(macIndex));
			data.setTimeOffset(cursor.getInt(timeOffsetIndex));
			data.setSrcData(cursor.getString(srcDataIndex));
			data.setUtc(cursor.getInt(utcIndex));
			//add list
			items.add(data);
		}
		cursor.close();
		return items;
	}
	



	private static Date toMeasureDate(String time){
		if(time==null || time.length()==0){
			return null;
		}
		try {
			return dateFormat.parse(time);
		} catch (Exception e) {
			e.printStackTrace();
			return null;
		}
	}
	
	
	public static String toStorageTime(int utc){
		try {
			return dateFormat.format(new Date(utc*1000L));
		} catch (Exception e) {
			e.printStackTrace();
			return "---";
		}
	}
}
