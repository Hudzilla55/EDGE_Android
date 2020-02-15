package com.healthsaas.edge.safe.crypto;

import android.text.TextUtils;

import java.security.InvalidAlgorithmParameterException;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;

import javax.crypto.BadPaddingException;
import javax.crypto.Cipher;
import javax.crypto.IllegalBlockSizeException;
import javax.crypto.KeyGenerator;
import javax.crypto.NoSuchPaddingException;
import javax.crypto.SecretKey;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;

import org.bouncycastle.crypto.PBEParametersGenerator;
import org.bouncycastle.crypto.generators.PKCS5S2ParametersGenerator;
import org.bouncycastle.crypto.params.KeyParameter;
import org.bouncycastle.util.encoders.Base64;

public final class AES {

	private static final String KEY = "230f872e-1a6f-40fb-9307-a2bdec3169b2";
	private static final String IV = "56B835E6-5DD2-4A99-8E8F-80D5E6E2129E";

	public static String encrypt(String text)
			throws IllegalBlockSizeException, BadPaddingException,
			InvalidKeyException, InvalidAlgorithmParameterException,
			NoSuchAlgorithmException, NoSuchPaddingException {

		String result = "";

		if (text != null && !TextUtils.isEmpty(text.trim())) {
			Keys keys = Keys.getInstance();
			byte[] keyBytes = keys.getKey();
			byte[] ivBytes = keys.getIV();

			if (keyBytes == null) {
				keyBytes = generateKey(KEY + System.currentTimeMillis());
				keys.setKey(keyBytes);

			}
			if (ivBytes == null) {
				ivBytes = getRawKey(IV + System.currentTimeMillis());
				keys.setIV(ivBytes);
			}

			if (keyBytes != null && ivBytes != null) {
				byte[] input = text.getBytes();
				SecretKeySpec key = new SecretKeySpec(keyBytes, "AES");
				IvParameterSpec ivSpec = new IvParameterSpec(ivBytes);
				Cipher cipher = Cipher.getInstance("AES/CBC/PKCS5Padding");
				cipher.init(Cipher.ENCRYPT_MODE, key, ivSpec);
				byte[] cipherText = cipher.doFinal(input);
				byte[] resultBytes = Base64.encode(cipherText);

				result = new String(resultBytes);
			}
		}
		return result;

	}

	public static String decrypt(String text)
			throws IllegalBlockSizeException, BadPaddingException,
			InvalidKeyException, InvalidAlgorithmParameterException,
			NoSuchAlgorithmException, NoSuchPaddingException {

		String result = "";

		if (text != null && !TextUtils.isEmpty(text.trim())) {

			Keys keys = Keys.getInstance();
			byte[] keyBytes = keys.getKey();
			byte[] ivBytes = keys.getIV();

			if (keyBytes == null) {
				keyBytes = generateKey(KEY + System.currentTimeMillis());
				keys.setKey(keyBytes);

			}
			if (ivBytes == null) {
				ivBytes = getRawKey(IV + System.currentTimeMillis());
				keys.setIV(ivBytes);
			}

			if (keyBytes != null && ivBytes != null) {
				byte[] cipherText = Base64.decode(text);
				SecretKeySpec key = new SecretKeySpec(keyBytes, "AES");
				IvParameterSpec ivSpec = new IvParameterSpec(ivBytes);
				Cipher cipher = Cipher.getInstance("AES/CBC/PKCS5Padding");
				cipher.init(Cipher.DECRYPT_MODE, key, ivSpec);
				byte[] plainText = cipher.doFinal(cipherText);

				result = new String(plainText);
			}
		}
		return result;

	}

	private static byte[] generateKey(String key) {
		PBEParametersGenerator generator = new PKCS5S2ParametersGenerator();
		generator.init(PBEParametersGenerator.PKCS5PasswordToUTF8Bytes((key).toCharArray()), null, 1);
		KeyParameter params = (KeyParameter) generator.generateDerivedParameters(256);

		return params.getKey();
	}

	private static byte[] getRawKey(String key)
			throws NoSuchAlgorithmException {

		byte[] seed = key.getBytes();
		KeyGenerator kgen = KeyGenerator.getInstance("AES");
		SecureRandom sr = SecureRandom.getInstance("SHA1PRNG");
		sr.setSeed(seed);
		kgen.init(128, sr);
		SecretKey skey = kgen.generateKey();
		byte[] raw = skey.getEncoded();
		return raw;
	}

}
