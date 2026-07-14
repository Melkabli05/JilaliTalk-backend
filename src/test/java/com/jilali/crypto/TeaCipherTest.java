package com.jilali.crypto;

import org.junit.jupiter.api.Test;

import java.security.SecureRandom;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Direct tests for {@link TeaCipher} in its "bare" form (explicit key, no key-embedding framing)
 * — the shape {@code ht_im/sock}'s {@code SESSION_KEY} mode uses. {@link Cc2018Cipher} already
 * carries the strongest evidence for this cipher core (vectors from the real native code, one
 * verified against live production traffic) since it's the same extracted implementation; these
 * tests cover the bare API surface that isn't otherwise exercised.
 */
class TeaCipherTest {

    @Test
    void encryptThenDecryptRoundTripsForVariousLengths() {
        SecureRandom random = new SecureRandom();
        byte[] key = new byte[16];
        random.nextBytes(key);

        for (int len : new int[] { 1, 7, 8, 9, 63, 64, 65, 500 }) {
            byte[] plaintext = new byte[len];
            random.nextBytes(plaintext);

            byte[] ciphertext = TeaCipher.encrypt(plaintext, key);
            byte[] decoded = TeaCipher.decrypt(ciphertext, key);

            assertArrayEquals(plaintext, decoded, "round trip failed for length " + len);
        }
    }

    @Test
    void rejectsEmptyPlaintext() {
        assertThrows(IllegalArgumentException.class, () -> TeaCipher.encrypt(new byte[0], new byte[16]));
    }

    @Test
    void rejectsWrongKeyLength() {
        assertThrows(IllegalArgumentException.class, () -> TeaCipher.encrypt("x".getBytes(), new byte[15]));
        assertThrows(IllegalArgumentException.class, () -> TeaCipher.decrypt(new byte[8], new byte[17]));
    }

    @Test
    void rejectsCiphertextLengthNotMultipleOfEight() {
        assertThrows(IllegalArgumentException.class, () -> TeaCipher.decrypt(new byte[9], new byte[16]));
    }

    @Test
    void decryptingWithWrongKeyFailsTrailerCheck() {
        byte[] plaintext = "some plaintext of reasonable length".getBytes();
        byte[] key = new byte[16];
        new SecureRandom().nextBytes(key);
        byte[] ciphertext = TeaCipher.encrypt(plaintext, key);

        byte[] wrongKey = key.clone();
        wrongKey[0] ^= 0x01;

        assertThrows(IllegalStateException.class, () -> TeaCipher.decrypt(ciphertext, wrongKey));
    }
}
