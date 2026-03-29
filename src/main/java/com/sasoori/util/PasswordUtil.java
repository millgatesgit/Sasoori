package com.sasoori.util;

import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.security.spec.InvalidKeySpecException;
import java.util.Base64;

/**
 * PBKDF2WithHmacSHA256 password hashing — no external dependencies.
 *
 * Stored format: {@code base64(salt):base64(hash)}
 */
public final class PasswordUtil {

    private static final String ALGORITHM  = "PBKDF2WithHmacSHA256";
    private static final int    ITERATIONS = 310_000;   // OWASP 2023 minimum
    private static final int    KEY_BITS   = 256;
    private static final int    SALT_BYTES = 16;

    private static final SecureRandom RANDOM = new SecureRandom();

    private PasswordUtil() {}

    /** Returns a new {@code salt:hash} string suitable for storing in {@code users.password_hash}. */
    public static String hash(String password) {
        byte[] salt = new byte[SALT_BYTES];
        RANDOM.nextBytes(salt);
        byte[] hash = derive(password.toCharArray(), salt);
        return Base64.getEncoder().encodeToString(salt)
                + ":" + Base64.getEncoder().encodeToString(hash);
    }

    /**
     * Returns {@code true} if {@code candidate} matches the stored {@code saltAndHash}.
     * Runs in constant time to prevent timing attacks.
     */
    public static boolean verify(String candidate, String saltAndHash) {
        if (candidate == null || saltAndHash == null) return false;
        String[] parts = saltAndHash.split(":", 2);
        if (parts.length != 2) return false;
        byte[] salt     = Base64.getDecoder().decode(parts[0]);
        byte[] expected = Base64.getDecoder().decode(parts[1]);
        byte[] actual   = derive(candidate.toCharArray(), salt);
        return constantTimeEquals(expected, actual);
    }

    private static byte[] derive(char[] password, byte[] salt) {
        try {
            PBEKeySpec spec = new PBEKeySpec(password, salt, ITERATIONS, KEY_BITS);
            SecretKeyFactory f = SecretKeyFactory.getInstance(ALGORITHM);
            return f.generateSecret(spec).getEncoded();
        } catch (NoSuchAlgorithmException | InvalidKeySpecException e) {
            throw new RuntimeException("PBKDF2 unavailable", e);
        }
    }

    private static boolean constantTimeEquals(byte[] a, byte[] b) {
        if (a.length != b.length) return false;
        int diff = 0;
        for (int i = 0; i < a.length; i++) diff |= a[i] ^ b[i];
        return diff == 0;
    }
}
