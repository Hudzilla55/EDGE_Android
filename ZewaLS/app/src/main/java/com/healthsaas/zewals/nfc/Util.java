package com.healthsaas.zewals.nfc;

import android.content.Intent;
import android.nfc.NdefMessage;
import android.nfc.NdefRecord;
import android.nfc.NfcAdapter;
import android.os.Parcelable;
import android.util.Log;


import com.healthsaas.zewals.Constants;

import java.nio.charset.Charset;

/**
 * Created by alexm on 10/8/17.
 */

public class Util {
    private static final String TAG="nfc.util";

    public static String getNfcDomain(String[] tokens) {
        if ((tokens == null) || tokens[Constants.TOKEN_DOMAIN].isEmpty())
            return null;

        return tokens[Constants.TOKEN_DOMAIN];
    }

    /******************************************************************************
     **********************************Read From NFC Tag***************************
     ******************************************************************************/
    public static void readFromIntent(Intent intent) {
        String action = intent.getAction();
        if (NfcAdapter.ACTION_TAG_DISCOVERED.equals(action)
                || NfcAdapter.ACTION_TECH_DISCOVERED.equals(action)
                || NfcAdapter.ACTION_NDEF_DISCOVERED.equals(action)) {
            Parcelable[] rawMsgs = intent.getParcelableArrayExtra(NfcAdapter.EXTRA_NDEF_MESSAGES);
            NdefMessage[] msgs = null;
            if (rawMsgs != null) {
                msgs = new NdefMessage[rawMsgs.length];
                for (int i = 0; i < rawMsgs.length; i++) {
                    msgs[i] = (NdefMessage) rawMsgs[i];
                }
            }
            buildTagViews(msgs);
        }
    }

    private static void buildTagViews(NdefMessage[] msgs) {
        if (msgs == null || msgs.length == 0) return;

        String text = "";
//        String tagId = new String(msgs[0].getRecords()[0].getType());
        NdefRecord record = msgs[0].getRecords()[0];
        if( record.getTnf() == NdefRecord.TNF_EXTERNAL_TYPE ) {
            String[] domainMime = (new String( record.getType(), Charset.forName("UTF-8"))).split(":");
            String domain = domainMime[0];
            String mime = domainMime[1];

            text = String.format("Domain=%s, Action=%s\n", domain, mime);
            byte[] payload = record.getPayload();
            if( payload != null ) {
                text += new String(payload, Charset.forName("UTF-8"));
            }
            Log.d(TAG, "NFC ExtTag="+text);
        }
    }

}
