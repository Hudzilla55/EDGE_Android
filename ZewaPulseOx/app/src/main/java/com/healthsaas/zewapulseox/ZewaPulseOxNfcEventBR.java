package com.healthsaas.zewapulseox;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.nfc.NdefMessage;
import android.nfc.NdefRecord;
import android.os.Bundle;
import android.os.Parcelable;
import android.util.Log;

import java.io.UnsupportedEncodingException;

import static com.healthsaas.zewapulseox.nfc.Util.getNfcDomain;

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

public class ZewaPulseOxNfcEventBR extends BroadcastReceiver {
    private static final String TAG = ZewaPulseOxNfcEventBR.class.getSimpleName();
    Context mAppContext = null;

    public ZewaPulseOxNfcEventBR() {
        super();
    }

    @Override
    public void onReceive(Context context, Intent intent) {
        final String MIME_TYPE = "text/plain";
        mAppContext = context.getApplicationContext();

        String action = intent.getAction();
        Log.d(TAG, "action=" + action);
        if (Constants.NFC_TECH_ACTION.equals(action) ||
                Constants.NFC_TAG_ACTION.equals(action) ||
                Constants.NFC_NDEF_ACTION.equals(action)) {
            Bundle bundle = intent.getExtras();
            if (bundle != null) {
                Parcelable[] messages = bundle.getParcelableArray("android.nfc.extra.NDEF_MESSAGES");
                if (messages != null) {
                    NdefMessage ndefMsg =  (NdefMessage)messages[0];
                    if (ndefMsg != null) {
                        NdefRecord[] ndefRecords = ndefMsg.getRecords();
                        for (NdefRecord ndefRec : ndefRecords) {
                            String str;
                            if (MIME_TYPE.equals(ndefRec.toMimeType())) {
                                try {
                                    byte[] bytes = ndefRec.getPayload();
                                    str = new String(bytes, 3, bytes.length - 3, "UTF-8");
                                } catch (UnsupportedEncodingException e) {
                                    Log.w(TAG, " not UTF-8");
                                    str = null;
                                }
                                String[] tokens = parseNfcText(str);
                                if (tokens != null) {
                                    if (Constants.COM_HEALTHSAAS_ZEWAPULSEOX.equals(getNfcDomain(tokens))) {
                                        handleDomain(tokens);
                                    }
                                }
                            }
                        }
                    }
                }
            }
        } else if (Constants.BLE_SCAN_COUNT_UPDATE.equals(action)) {
            Intent i = new Intent(mAppContext, ZewaPulseOxManager.class);
            i.setAction(ZewaPulseOxManager.ACTION_SCAN_EVENT);
            i.putExtras(intent);
            mAppContext.startService(i);
        }
    }

    private String[] parseNfcText(String nfcText) {
        if (nfcText == null) {
            return null;
        }
        return nfcText.split(Constants.TOKEN_SEPARATOR, Constants.MAX_TOKENS);
    }

    private void handleDomain(String[] tokens) {
        int cmdId = getCmdId(tokens);

        switch (cmdId) {
            case Constants.CMD_BT_PAIR: {
                String model = getBtModel(tokens);
                String bt_mac = getBtMac(tokens);
                String bt_pin = getBtPin(tokens);
                Log.d(TAG, "model=" + model + ", BT_MAC=" + bt_mac + ", BT_PIN=" + bt_pin);

                sendZewaManagerIntent(Constants.CMD_BT_PAIR, model, bt_mac);
            }
            break;

            case Constants.CMD_BT_UNPAIR: {
                String model = getBtModel(tokens);
                String bt_mac = getBtMac(tokens);
                Log.d(TAG, "model=" + model + ", BT_MAC=" + bt_mac);

                sendZewaManagerIntent(Constants.CMD_BT_UNPAIR, model, bt_mac);
            }
            break;

            case Constants.CMD_BT_UNPAIR_ALL: {
                String model = getBtModel(tokens);
                String bt_mac = getBtMac(tokens);
                Log.d(TAG, "model=" + model + ", BT_MAC=" + bt_mac);

                sendZewaManagerIntent(Constants.CMD_BT_UNPAIR_ALL, model, bt_mac);
            }
            break;

            default:
                Log.d(TAG, "Unimplemented KEY_CMD_ID: " + String.valueOf(cmdId));
        }
    }

    private void sendZewaManagerIntent(int cmdId, String model, String bt_mac) {
        Bundle bundle = new Bundle();
        bundle.putInt(ZewaPulseOxManager.KEY_CMD_ID, cmdId);
        bundle.putString(ZewaPulseOxManager.KEY_MODEL_NAME, model);
        bundle.putString(ZewaPulseOxManager.KEY_BT_MAC, bt_mac);

        Intent intent = new Intent(mAppContext, ZewaPulseOxManager.class);
        intent.setAction(ZewaPulseOxManager.ACTION_NFC_EVENT)
                .putExtras(bundle);

        mAppContext.startService(intent);
    }

    private int getCmdId(String[] tokens) {
        int cmdId = -1;

        if ((tokens == null) || (tokens.length < 2))
            return cmdId;

        try {
            cmdId = Integer.parseInt(tokens[Constants.TOKEN_CMD]);
        } catch (NumberFormatException e) {
            cmdId = -1;
            Log.e(TAG, "Invalid NFC CMD ID: " + e.toString());
        }
        return cmdId;
    }

    private String getBtModel(String[] tokens) {
        if ((tokens == null) || tokens[Constants.TOKEN_MODEL].isEmpty())
            return null;

        return tokens[Constants.TOKEN_MODEL];
    }

    private String getBtMac(String[] tokens) {
        if ((tokens == null) || tokens[Constants.TOKEN_BT_MAC].isEmpty())
            return null;

        return tokens[Constants.TOKEN_BT_MAC];
    }

    private String getBtPin(String[] tokens) {
        if ((tokens == null) || tokens[Constants.TOKEN_PIN].isEmpty())
            return null;

        return tokens[Constants.TOKEN_PIN];
    }
}

