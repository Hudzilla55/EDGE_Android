package com.healthsaas.zewamedwell;

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
    static final String DOMAIN_HEALTHSAAS_ZEWAMEDWELL = "com.healthsaas.zewamedwell";

    // make sure doesn't clash with com.healthsaas.zewamedwell.IntelConstants.
//    public static final int CMD_DATA_START = 51;
//    public static final int CMD_DATA_STOP  = 52;
//
//    public static final int CMD_PATIENT_ID = 55;

    static final String MGR_NAME = "MedWell Manager";
    static final String MGR_RUNNING = MGR_NAME + " Service running.";
    static final int MGR_NOTIFY_ID = 202;

    public static final String ZEWA_MFG_NAME = "Zewa";

    public static final String ZEWA_MODEL_MEDWELL = "Zewa MedWell";

    static final int TMO_MEDWELL_SEARCH = 30 * 1000;
    static final int TMO_MEDWELL_NEW_DATA_SEARCH = 12 * 1000;
    static final int TMO_MEDWELL_NEW_DATA_SEARCH_PAUSE = 12 * 1000;
    static final int TMO_MEDWELL_SYNC = 5 * 60 * 1000;

    static final int STATE_TRANSITION_TIME = 200;

    public static final String MEDWELL_DATA_TYPE = "PILLBOX";
    public static final String MEDWELL_PREFS_NAME = "medwell_prefs";
    public static final String MEDWELL_ROOT_URL_DEFAULT = "https://demo.ourconnectedhealth.com/ws/austonio/";
    static final String MEDWELL_REMINDER_PATH = "medwell.svc/getreminders";
    static final String MEDWELL_SORTVER_PATH = "medwell.svc/getsortver";
    static final String MEDWELL_REMINDER_REQ_PARAM = "sn";
    public static final String PREFS_NAME = "medwell_prefs";

    static final String BLE_SCAN_COUNT_UPDATE = "com.healthsaas.BLE_SCAN_COUNT_UPDATE";
    static final String ISO8601_TZ_FORMAT_STRING = "ZZZZZ";
    static final String ISO8601_SHORT_FORMAT_STRING = "yyyy-MM-dd HH:mm:ss";
    static final String ISO8601_FORMAT_STRING = ISO8601_SHORT_FORMAT_STRING + ISO8601_TZ_FORMAT_STRING;


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
    static final int TOKEN_PIN = 3;
    static final int TOKEN_BT_MAC = 4;

    // NFC Decommission indices
//    public static final int TOKEN_ANDROID_ID = 3;

    // NFC BT Extended Pairing
//    public static final int TOKEN_JSON = 3;

//   public static final String DOMAIN_INTEL = "com.intel.rpm";

    static final int CMD_BT_PAIR = 2;
    static final int CMD_BT_UNPAIR = 3;
    static final int CMD_BT_UNPAIR_ALL = 4;
//    public static final int CMD_DECOMMISSION = 10;
//    public static final int CMD_CALL_HOME = 11;
//    public static final int CMD_CFG_WIFI = 19;
//    public static final int CMD_BT_EXT_PAIR = 24;

    static final String BT_STATUS_SEARCHING = "Bluetooth Searching";
    static final String BT_STATUS_PAIR_NOT_FOUND = "Device not found for Pairing";
    static final String BT_STATUS_PAIR_OK = "Pairing Successful";
    //static final String BT_STATUS_PAIR_FAIL = "Pairing Failed";
    static final String BT_STATUS_UNPAIR_OK = "Unpairing Successful";
    //    public static final String BT_STATUS_UNPAIR_FAIL = "BT_UNPAIRING_FAILED";
    static final String BT_STATUS_UNPAIR_NOT_FOUND = "Device not found for Unpairing";

}
