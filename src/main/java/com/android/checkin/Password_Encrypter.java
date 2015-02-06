package com.android.checkin;

import org.apache.commons.codec.binary.Base64;
import java.lang.System;
import javax.crypto.*;
import java.security.*;
import java.security.spec.*;
import java.math.BigInteger;
import java.io.UnsupportedEncodingException;

public class Password_Encrypter {

	public static String default_key = "AAAAgMom/1a/v0lblO2Ubrt60J2gcuXSljGFQXgcyZWveWLEwo6prwgi3iJIZdodyhKZQrNWp5nKJ3srRXcUW+F1BD3baEVGcmEgqaLZUNBjm057pKRI16kB0YppeGx5qIQ5QjKzsR8ETQbKLNWgRY0QRNVz34kMJR3P/LgHax/6rmf5AAAAAwEAAQ==";

	public static String cipher_attr = "RSA/ECB/OAEPWITHSHA1ANDMGF1PADDING";

	public static String encryptPassword(String email, String password) {
		
		//String email = "android.test.seclab@gmail.com";
		//String password = "android@test@seclab";

		// encryptPasswd()
		String combined = email + "\u0000" + password;


		try {
			// encryptString()

			// createKeyFromString()
			byte[] cipherTextHeader;
			//byte[] decoded_key = Base64.getDecoder().decode(default_key);
			byte[] decoded_key = Base64.decodeBase64(default_key);


			int i = ( 0x0 | (0xFF & decoded_key[0]) << 24 | (0xFF & decoded_key[1]) << 16 | (0xFF & decoded_key[2]) << 8 | 0xFF & decoded_key[3] );
			byte[] temp = new byte[i];
			System.arraycopy(decoded_key , 4, temp, 0, i);
			BigInteger b1 = new BigInteger(1, temp); 	
			
			int j = ( 0x0 | (0xFF & decoded_key[i + 4]) << 24 | (0xFF & decoded_key[i + 5]) << 16 | (0xFF & decoded_key[i + 6]) << 8 | 0xFF & decoded_key[i + 7] );
			byte[] temp2 = new byte[j];
			System.arraycopy(decoded_key , i + 8, temp2, 0, j);
			BigInteger b2 = new BigInteger(1, temp2);
						
			byte[] sha1 = MessageDigest.getInstance("SHA-1").digest(decoded_key);
			
			cipherTextHeader = new byte[5];
			cipherTextHeader[0] = 0;
			System.arraycopy(sha1, 0, cipherTextHeader, 1, 4);

			PublicKey pk = KeyFactory.getInstance("RSA").generatePublic(new RSAPublicKeySpec(b1,b2));

			// encryptString()
			Cipher c = Cipher.getInstance(cipher_attr);

			byte[] x = combined.getBytes("UTF-8");

			c.init(c.PUBLIC_KEY, pk);
			byte[] something = c.doFinal(x);

			byte[] output = new byte[133];
			System.arraycopy(cipherTextHeader, 0, output, 0, cipherTextHeader.length);
			System.arraycopy(something, 0, output, cipherTextHeader.length, something.length);

			//System.out.println(Base64.getEncoder().encodeToString(output));
			return Base64.encodeBase64URLSafeString(output);
		} catch ( NoSuchAlgorithmException|InvalidKeySpecException|UnsupportedEncodingException|InvalidKeyException|IllegalBlockSizeException|NoSuchPaddingException|BadPaddingException ex ) {
			System.out.println("poop");
		}
		return "";
	}


	}
