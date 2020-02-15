package com.healthsaas.zewals;

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

public class Constants {

    /*
     *  NFC card constants
     */
    static final String DOMAIN_HEALTHSAAS_ZEWA = "com.healthsaas.zewals";

    // make sure doesn't clash with com.healthsaas.zewals.Constants.
//    public static final int CMD_DATA_START = 51;
//    public static final int CMD_DATA_STOP  = 52;
//
//    public static final int CMD_PATIENT_ID = 55;

    static final String MGR_NAME = "LS Manager";
    static final String MGR_RUNNING = MGR_NAME + " Service running.";
    static final int MGR_NOTIFY_ID = 101;

    public static final String ZEWA_MFG_NAME = "Zewa";
    static final String ZEWA_MODEL_BPM_GENERIC = "Zewa BPM";
    static final String ZEWA_MODEL_BPM_1 = "Zewa UAM-910BT"; // 2 user
    static final String ZEWA_MODEL_BPM_2 = "Zewa UAM-880"; // 2 user
    static final String ZEWA_MODEL_BPM_3 = "Zewa UAM-820BT"; // 1 user
    static final String ZEWA_MODEL_BPM_4 = "Zewa WS-380"; // 2 user

    static final String ZEWA_MODEL_WS_GENERIC = "Zewa WEIGHT";
    static final String ZEWA_MODEL_WS_1 = "Zewa 21300";

    static final String ZEWA_MODEL_ACTRKR_GENERIC = "Zewa ACTRKR";
    public static final String ZEWA_MODEL_ACTRKR_1 = "Zewa 21200";

    static final int TMO_ZEWA_SEARCH = 30 * 1000;
    static final int TMO_ZEWA_PAIR   = 45 * 1000;

    static final int STATE_TRANSITION_TIME = 200;

    static final String PREFS_NAME = "zewals_prefs";
    static final String PREFS_PAIRED_DEVICES = "BT_MAC_PairedDevices";

    static final String ACTRKR_DATA_TYPE = "ACTRKR";
    static final String ACTRKR_WALK_STEPS = "walk_steps";
    static final String ACTRKR_RUN_STEPS = "run_steps";
    static final String ACTRKR_DISTANCE = "distance";
    static final String ACTRKR_EXERCISE_TIME = "exercise_time";
    static final String ACTRKR_CALORIES = "calories";
    static final String ACTRKR_INTENSITY = "intensity_level";
    static final String ACTRKR_SLEEP = "sleep_status";
    static final String ACTRKR_EXAMOUNT = "exercise_amount";
    static final String DEVICE_BATTERY = "battery";
    static final String TIMEZONE_OFFSET = "timezone_offset";

    static final String ROOT_URL_DEFAULT = "https://demo.ourconnectedhealth.com/ws/austonio/";
    static final String BLE_SCAN_COUNT_UPDATE = "com.healthsaas.BLE_SCAN_COUNT_UPDATE";
    static final String ISO8601_TZ_FORMAT_STRING = "ZZZZZ";
    static final String ISO8601_SHORT_FORMAT_STRING = "yyyy-MM-dd HH:mm:ss";
    static final String ISO8601_FORMAT_STRING = ISO8601_SHORT_FORMAT_STRING + ISO8601_TZ_FORMAT_STRING;


/*
/
/
 */
static final String NFC_TAG_ACTION = "archer.android.nfc.action.TAG_DISCOVERED";
    static final String NFC_TECH_ACTION = "archer.android.nfc.action.TECH_DISCOVERED";
    static final String NFC_NDEF_ACTION = "archer.android.nfc.action.NDEF_DISCOVERED";

    static final String TOKEN_SEPARATOR = ";;";
    static final int MAX_TOKENS = 8;

    // NFC common token indices
    public static final int TOKEN_DOMAIN = 0;
    static final int TOKEN_CMD = 1;

    // NFC BT Pair/Unpair token indices
    static final int TOKEN_MODEL = 2;
    //    public static final int TOKEN_PIN = 3;
    static final int TOKEN_BT_MAC = 4;

    // NFC Decommission indices
//    public static final int TOKEN_ANDROID_ID = 3;

    // NFC BT Extended Pairing
//    public static final int TOKEN_JSON = 3;


    static final int CMD_BT_PAIR = 2;
    static final int CMD_BT_UNPAIR = 3;
    static final int CMD_BT_UNPAIR_ALL = 4;
    static final int CMD_BT_PAIR_CODE = 5;
    static final int CMD_BT_PAIR_CANCEL = 0;
//    public static final int CMD_DECOMMISSION = 10;
//    public static final int CMD_CALL_HOME = 11;
//    public static final int CMD_CFG_WIFI = 19;
//    public static final int CMD_BT_EXT_PAIR = 24;

    static final String BT_STATUS_SEARCHING = "Bluetooth Searching";
    static final String BT_STATUS_SEARCHING_CANCEL = "Bluetooth Searching Canceled";
    static final String BT_STATUS_PAIR_NOT_FOUND = "Device not found for Pairing";
    static final String BT_STATUS_PAIR_OK = "Pairing Successful";
    static final String BT_STATUS_PAIR_FAIL = "Pairing Failed";
    static final String BT_STATUS_UNPAIR_OK = "Unpairing Successful";
    //    public static final String BT_STATUS_UNPAIR_FAIL = "BT_UNPAIRING_FAILED";
    static final String BT_STATUS_UNPAIR_NOT_FOUND = "Device not found for Unpairing";

}
