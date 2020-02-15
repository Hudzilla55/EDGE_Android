package com.healthsaas.zewals.zewadb;

import android.content.Context;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.util.Log;


public class SleepDataReaderHelper extends SQLiteOpenHelper {

	/**
	 * 创建数据库
	 */
	private static final String SQL_CREATE_ENTRIES =
			"CREATE TABLE " + SleepDataReaderContract.SleepDataEntry.TABLE_NAME + " (" +
					SleepDataReaderContract.SleepDataEntry._ID + " INTEGER PRIMARY KEY," +
					SleepDataReaderContract.SleepDataEntry.COLUMN_NAME_MAC + " TEXT," +
					SleepDataReaderContract.SleepDataEntry.COLUMN_NAME_SRC_DATA + " TEXT," +
					SleepDataReaderContract.SleepDataEntry.COLUMN_NAME_TIME_OFFSET + " INTEGER," +
					SleepDataReaderContract.SleepDataEntry.COLUMN_NAME_UTC+" INTEGER )";

	/**
	 * 删除数据库
	 */
	private static final String SQL_DELETE_ENTRIES =
			"DROP TABLE IF EXISTS " + SleepDataReaderContract.SleepDataEntry.TABLE_NAME;
	// If you change the database schema, you must increment the database version.
	public static final int DATABASE_VERSION = 1;
	public static final String DATABASE_NAME = "BluetoothDemo.db";



	public SleepDataReaderHelper(Context context) {
		super(context, DATABASE_NAME, null, DATABASE_VERSION);
	}
	public void onCreate(SQLiteDatabase db) {
		db.execSQL(SQL_CREATE_ENTRIES);
	}
	public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
		Log.e("LS-NFC", "Updating table from " + oldVersion + " to " + newVersion);
		if(oldVersion == 1){
			db.execSQL("ALTER TABLE " + SleepDataReaderContract.SleepDataEntry.TABLE_NAME  + " ADD COLUMN "+SleepDataReaderContract.SleepDataEntry.COLUMN_NAME_UTC+" INTEGER;");
		}
		/*
		//Added new column to book table - book rating 
		if (oldVersion < 2){
			db.execSQL(DROP + BookEntry.TABLE_NAME);
			db.execSQL(BookEntry.SQL_CREATE_BOOK_ENTRY_TABLE);
		}
		//Rename table to book_information - this is where things will start failing.
		if (oldVersion < 3){
			db.execSQL(DROP + BookEntry.TABLE_NAME);
			db.execSQL(BookEntry.SQL_CREATE_BOOK_ENTRY_TABLE);
		}
		// Add new column for a calculated value. By this time, if I am upgrading from version 2 to 
		// version 4, my table would already contain the new column I am trying to add below, 
		// which would result in a SQLException. These situations are sometimes difficult to spot, 
		// as you basically need to test from every different version of database to upgrade from. 
		// Some upgrades might work and some might fail with this method.
		// It is best to follow the other method that is on the master branch of this repo.
		if (oldVersion < 4){
			db.execSQL("ALTER TABLE " + BookEntry.TABLE_NAME  + " ADD COLUMN calculated_pages_times_rating INTEGER;");
		}
		//As you can probably imagine, this is a terrible way to do upgrades, Please DONT DO IT!!!!
		*/
	}
	public void onDowngrade(SQLiteDatabase db, int oldVersion, int newVersion) {
		onUpgrade(db, oldVersion, newVersion);
	}
}
