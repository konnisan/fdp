package com.delivery.fdp.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Base64;

@Service
public class CredentialCrypto {
    private static final int NONCE_BYTES = 12;
    private static final int TAG_BITS = 128;
    private final byte[] keyBytes;
    private final SecureRandom random = new SecureRandom();

    public CredentialCrypto(@Value("${fdp.security.credential-key:}") String encodedKey) {
        if (!StringUtils.hasText(encodedKey)) {
            this.keyBytes = null;
            return;
        }
        try {
            byte[] decoded = Base64.getDecoder().decode(encodedKey.trim());
            if (decoded.length != 32) {
                throw new IllegalArgumentException("FDP_CREDENTIAL_KEY must be a Base64 encoded 32-byte key");
            }
            this.keyBytes = decoded;
        } catch (IllegalArgumentException e) {
            throw new IllegalStateException("Invalid FDP_CREDENTIAL_KEY: " + e.getMessage(), e);
        }
    }

    public boolean configured() {
        return keyBytes != null;
    }

    public String encrypt(String plaintext) {
        if (!StringUtils.hasText(plaintext)) throw new IllegalArgumentException("token is required");
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

    private void requireConfigured() {
        if (!configured()) {
            throw new IllegalStateException("FDP_CREDENTIAL_KEY is not configured. Generate one with: openssl rand -base64 32");
        }
    }
}
