package wfDataModel.model.util;

import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.security.InvalidAlgorithmParameterException;
import java.security.InvalidKeyException;
import java.security.KeyFactory;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.PublicKey;
import java.security.SecureRandom;
import java.security.spec.InvalidKeySpecException;
import java.security.spec.X509EncodedKeySpec;
import java.util.Arrays;
import java.util.Base64;

import javax.crypto.BadPaddingException;
import javax.crypto.Cipher;
import javax.crypto.IllegalBlockSizeException;
import javax.crypto.KeyAgreement;
import javax.crypto.NoSuchPaddingException;
import javax.crypto.SecretKey;
import javax.crypto.spec.DHParameterSpec;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;

import wfDataModel.model.logging.Log;

/**
 * Util for handling various authentication / encryption / decryption tasks
 * @author MatNova
 *
 */
public final class AuthUtil {

	// P and G values for DiffieHellman calculations
	private static BigInteger P = new BigInteger("133790237483147974554807246513602695719886215618865437486222514657300888741230864248355549958361665695613500272512792773265057305114074705723466961707744821493766460202517971203449864536208966325281429815403456732486788197410629008957160889380135193110291253149299917890337086237460321740829615241209754047387");
	private static BigInteger G = new BigInteger("2");
	private static int AES_SIZE = 128;
	
	/**
	 * Given some data, an AES key, and a seed, will attempt to encrypt the data using the AES key with the provided seed.
	 * @param data
	 * @param aes
	 * @param seed
	 * @return
	 */
	public static String encode(String data, byte[] aes, byte[] seed) {
		String encoded = null;
		try {
			encoded = performCipher(data.getBytes(StandardCharsets.UTF_8), aes, seed, true);
		} catch (Exception e) {
			Log.error("AuthUtil.encode() : Exception -> ", e);
		}
		return encoded;
	}

	/**
	 * Given some data that is base64 encoded, an AES key, and a seed, will attempt to decrypt the data using the AES key with the provided seed.
	 * @param data
	 * @param aes
	 * @param seed
	 * @return
	 */
	public static String decode(String data, byte[] aes, byte[] seed) {
		String decoded = null;
		try {
			decoded = performCipher(Base64.getDecoder().decode(data), aes, seed, false);
		} catch (Exception e) {
			Log.error("AuthUtil.decode() : Exception -> ", e);
		}
		return decoded;
	}

	private static String performCipher(byte[] data, byte[] aes, byte[] seed, boolean isEncrypt) throws NoSuchAlgorithmException, InvalidKeySpecException, NoSuchPaddingException, IllegalBlockSizeException, BadPaddingException, InvalidKeyException, InvalidAlgorithmParameterException {
		IvParameterSpec ivParameterSpec = new IvParameterSpec(seed);
		SecretKey key = new SecretKeySpec(aes, "AES");
		Cipher cipher = Cipher.getInstance("AES/CBC/PKCS5PADDING");
		cipher.init(isEncrypt ? Cipher.ENCRYPT_MODE : Cipher.DECRYPT_MODE, key, ivParameterSpec);
		byte[] ciphData = cipher.doFinal(data);
		return isEncrypt ? Base64.getEncoder().encodeToString(ciphData) : new String(ciphData, StandardCharsets.UTF_8);
	}

	/**
	 * Will return a DiffieHellman key pair that can be used for a handshake.
	 * @return
	 * @throws InvalidAlgorithmParameterException
	 * @throws NoSuchAlgorithmException
	 */
	public static KeyPair generateDiffieHellman() throws InvalidAlgorithmParameterException, NoSuchAlgorithmException {
		KeyPairGenerator kpg = KeyPairGenerator.getInstance("DH"); 
		DHParameterSpec dhSpec = new DHParameterSpec( P, G); 
		kpg.initialize(dhSpec);
		return kpg.generateKeyPair(); 
	}
	
	/**
	 * Given a key agreement from a DiffieHellman handshake, and the public key of the other party, will generate an AES key that can be symmetrically used to encrypt and decrypt data
	 * @param agreement
	 * @param otherPublicKey
	 * @return
	 * @throws InvalidKeySpecException
	 * @throws NoSuchAlgorithmException
	 * @throws InvalidKeyException
	 * @throws IllegalStateException
	 */
	public static byte[] generateAESFromDiffieHellman(KeyAgreement agreement, byte[] otherPublicKey) throws InvalidKeySpecException, NoSuchAlgorithmException, InvalidKeyException, IllegalStateException {
		KeyFactory kf = KeyFactory.getInstance("DH"); 
		X509EncodedKeySpec bx509Spec = new X509EncodedKeySpec(otherPublicKey);
		PublicKey pk = kf.generatePublic(bx509Spec);
		agreement.doPhase(pk, true);
		// generates the secret key 
		byte bsecret[] = agreement.generateSecret(); 
		// generates an AES key 
        MessageDigest bsha256 = MessageDigest.getInstance("SHA-256"); 
        byte[] bskey = Arrays.copyOf(bsha256.digest(bsecret), AES_SIZE / Byte.SIZE);
        SecretKey bkey = new SecretKeySpec(bskey, "AES");
        return bkey.getEncoded();
	}
	
	/**
	 * Will return a cryptographically secure seed that can be used during the encryption process
	 * @return
	 */
	public static byte[] generateSeed() {
		byte[] initializationVector = new byte[16];
		SecureRandom secureRandom = new SecureRandom();
		secureRandom.nextBytes(initializationVector);
		return initializationVector;
	}
}
