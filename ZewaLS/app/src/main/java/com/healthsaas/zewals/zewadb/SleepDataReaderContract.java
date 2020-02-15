package com.healthsaas.zewals.zewadb;

import android.provider.BaseColumns;

public class SleepDataReaderContract {

	// To prevent someone from accidentally instantiating the contract class,
	// make the constructor private.
	private SleepDataReaderContract() {}

	/* Inner class that defines the table contents */
	public static class SleepDataEntry implements BaseColumns {
		public static final String TABLE_NAME = "sleep_data";
		public static final String COLUMN_NAME_MAC = "mac";//不带符号
		public static final String COLUMN_NAME_UTC="utc";
		public static final String COLUMN_NAME_TIME_OFFSET="timeOffset";
		public static final String COLUMN_NAME_SRC_DATA="srcData";

	}
}
