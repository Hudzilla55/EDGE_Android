package com.healthsaas.edge.safe.crypto;

import com.healthsaas.edge.safe.SafeApp;

import android.content.Context;
import android.content.SharedPreferences;
import android.content.SharedPreferences.Editor;
import android.util.Base64;

public final class Keys {

	private static Keys instance;
	private final SharedPreferences sharedPreferences;
	private final String KEY = "key";
	private final String IV = "iv";

	private Keys() {
		sharedPreferences = SafeApp.sAppContext.getSharedPreferences("SafeSDK_crypto_keys", Context.MODE_PRIVATE);
	}

	public static synchronized Keys getInstance() {
		if (instance == null) {
			instance = new Keys();
		}
		return instance;
	}

	void setKey(byte[] keyBytes) {
		Editor editor = sharedPreferences.edit();
		editor.putString(KEY, Base64.encodeToString(keyBytes, Base64.DEFAULT));
		editor.commit();
	}

	void setIV(byte[] ivBytes) {
		Editor editor = sharedPreferences.edit();
		editor.putString(IV, Base64.encodeToString(ivBytes, Base64.DEFAULT));
		editor.commit();
	}

	byte[] getKey() {
		String key = sharedPreferences.getString(KEY, null);
		byte[] keyBytes = null;
		if (key != null) {
			keyBytes = Base64.decode(key, Base64.DEFAULT);
		}
		return keyBytes;
	}

	byte[] getIV() {
		String iv = sharedPreferences.getString(IV, null);
		byte[] ivBytes = null;
		if (iv != null) {
			ivBytes = Base64.decode(iv, Base64.DEFAULT);
		}
		return ivBytes;
	}
}
