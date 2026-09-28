package bd.ac.kuet.campuscycle.domain;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;

/**
 * PBKDF2-HMAC-SHA256 password hashing with per-user random salt (P-006).
 *
 * <p>New hashes are stored as {@code pbkdf2-sha256$iterations$base64salt$base64hash}
 * (600 000 iterations, 16-byte salt, 256-bit key). {@link #verify} additionally accepts
 * legacy {@code sha256:} single-pass hashes for migration comparison only; every other
 * stored form — including plaintext — is rejected. Failures never leak the plaintext.
 */
public final class PasswordUtils {

    private PasswordUtils() {}

    private static final String PREFIX = "pbkdf2-sha256";
    private static final int ITERATIONS = 600_000;
    private static final int SALT_BYTES = 16;
    private static final int KEY_BITS = 256;

    /**
     * Hashes a password with PBKDF2-HMAC-SHA256 and a fresh random salt.
     *
     * @throws IllegalArgumentException if {@code password} is {@code null}
     * @throws AppError if the platform cannot perform the hash (never returns plaintext)
     */
    public static String hash(String password) {
        if (password == null) {
            throw new IllegalArgumentException("Password must not be null.");
        }
        try {
            byte[] salt = new byte[SALT_BYTES];
            new SecureRandom().nextBytes(salt);
            byte[] derived = pbkdf2(password.toCharArray(), salt, ITERATIONS, KEY_BITS);
            return PREFIX + "$" + ITERATIONS + "$"
                    + Base64.getEncoder().encodeToString(salt) + "$"
                    + Base64.getEncoder().encodeToString(derived);
        } catch (IllegalArgumentException e) {
            throw e;
        } catch (Exception e) {
            throw new AppError("HASH_FAILED", "Failed to hash password.", e);
        }
    }

    /**
     * Verifies a raw candidate password against stored credentials.
     * Accepts the {@code pbkdf2-sha256$} format and legacy {@code sha256:} hashes;
     * rejects everything else (no plaintext fallback).
     */
    public static boolean verify(String candidatePassword, String storedCredentials) {
        if (candidatePassword == null || storedCredentials == null) {
            return false;
        }
        if (storedCredentials.startsWith(PREFIX + "$")) {
            return verifyPbkdf2(candidatePassword, storedCredentials);
        }
        if (storedCredentials.startsWith("sha256:")) {
            return verifyLegacySha256(candidatePassword, storedCredentials);
        }
        return false;
    }

    private static boolean verifyPbkdf2(String candidatePassword, String storedCredentials) {
        try {
            String[] parts = storedCredentials.split("\\$", -1);
            if (parts.length != 4) {
                return false;
            }
            int iterations = Integer.parseInt(parts[1]);
            byte[] salt = Base64.getDecoder().decode(parts[2]);
            byte[] expected = Base64.getDecoder().decode(parts[3]);
            if (iterations <= 0 || salt.length == 0 || expected.length == 0) {
                return false;
            }
            byte[] actual = pbkdf2(candidatePassword.toCharArray(), salt, iterations, expected.length * 8);
            return MessageDigest.isEqual(actual, expected);
        } catch (Exception e) {
            return false;
        }
    }

    private static boolean verifyLegacySha256(String candidatePassword, String storedCredentials) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] bytes = md.digest(candidatePassword.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder("sha256:");
            for (byte b : bytes) {
                sb.append(String.format("%02x", b));
            }
            return MessageDigest.isEqual(
                    sb.toString().getBytes(StandardCharsets.UTF_8),
                    storedCredentials.getBytes(StandardCharsets.UTF_8));
        } catch (Exception e) {
            return false;
        }
    }

    private static byte[] pbkdf2(char[] password, byte[] salt, int iterations, int keyBits) throws Exception {
        SecretKeyFactory factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256");
        PBEKeySpec spec = new PBEKeySpec(password, salt, iterations, keyBits);
        try {
            return factory.generateSecret(spec).getEncoded();
        } finally {
            spec.clearPassword();
        }
    }
}
