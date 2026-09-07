package com.delivery.fdp.service;

import com.delivery.fdp.config.RuntimeProperties;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermission;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.Set;

@Service
public class CredentialCrypto {
    private static final int NONCE_BYTES = 12;
    private static final int TAG_BITS = 128;
    private static final int KEY_BYTES = 32;
    private final byte[] keyBytes;
    private final SecureRandom random = new SecureRandom();

    public CredentialCrypto(@Value("${fdp.security.credential-key:}") String encodedKey,
                            RuntimeProperties runtime) {
        this.keyBytes = StringUtils.hasText(encodedKey)
                ? decode(encodedKey.trim(), "FDP_CREDENTIAL_KEY")
                : loadOrCreateLocalKey(runtime);
    }

    public boolean configured() {
        return keyBytes != null;
    }

    public String encrypt(String plaintext) {
        if (!StringUtils.hasText(plaintext)) throw new IllegalArgumentException("value is required");
        requireConfigured();
        try {
            byte[] nonce = new byte[NONCE_BYTES];
            random.nextBytes(nonce);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, new SecretKeySpec(keyBytes, "AES"), new GCMParameterSpec(TAG_BITS, nonce));
            byte[] encrypted = cipher.doFinal(plaintext.getBytes(StandardCharsets.UTF_8));
            ByteBuffer payload = ByteBuffer.allocate(nonce.length + encrypted.length);
            payload.put(nonce).put(encrypted);
            return Base64.getEncoder().encodeToString(payload.array());
        } catch (Exception e) {
            throw new IllegalStateException("Credential encryption failed", e);
        }
    }

    public String decrypt(String encryptedValue) {
        requireConfigured();
        try {
            byte[] payload = Base64.getDecoder().decode(encryptedValue);
            if (payload.length <= NONCE_BYTES) throw new IllegalArgumentException("Encrypted credential payload is invalid");
            ByteBuffer buffer = ByteBuffer.wrap(payload);
            byte[] nonce = new byte[NONCE_BYTES];
            buffer.get(nonce);
            byte[] encrypted = new byte[buffer.remaining()];
            buffer.get(encrypted);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, new SecretKeySpec(keyBytes, "AES"), new GCMParameterSpec(TAG_BITS, nonce));
            return new String(cipher.doFinal(encrypted), StandardCharsets.UTF_8);
        } catch (Exception e) {
            throw new IllegalStateException("Credential decryption failed", e);
        }
    }

    private byte[] loadOrCreateLocalKey(RuntimeProperties runtime) {
        try {
            Path root = Path.of(runtime.getDataRoot()).toAbsolutePath().normalize();
            Files.createDirectories(root);
            Path file = root.resolve(".fdp-credential-key").normalize();
            if (!file.startsWith(root)) throw new IllegalStateException("Invalid FDP key path");
            if (Files.isRegularFile(file)) {
                return decode(Files.readString(file, StandardCharsets.UTF_8).trim(), "local FDP key");
            }
            byte[] generated = new byte[KEY_BYTES];
            random.nextBytes(generated);
            Files.writeString(file, Base64.getEncoder().encodeToString(generated), StandardCharsets.UTF_8);
            try {
                Files.setPosixFilePermissions(file, Set.of(PosixFilePermission.OWNER_READ, PosixFilePermission.OWNER_WRITE));
            } catch (UnsupportedOperationException ignored) {
                // Windows development filesystem.
            }
            return generated;
        } catch (Exception e) {
            throw new IllegalStateException("FDP could not create its local encryption key under FDP_DATA_ROOT", e);
        }
    }

    private byte[] decode(String encoded, String source) {
        try {
            byte[] decoded = Base64.getDecoder().decode(encoded);
            if (decoded.length != KEY_BYTES) {
                throw new IllegalArgumentException(source + " must be a Base64 encoded 32-byte key");
            }
            return decoded;
        } catch (IllegalArgumentException e) {
            throw new IllegalStateException("Invalid " + source + ": " + e.getMessage(), e);
        }
    }

    private void requireConfigured() {
        if (!configured()) throw new IllegalStateException("FDP encryption key is unavailable");
    }
}
