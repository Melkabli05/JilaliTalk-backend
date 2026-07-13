package com.jilali.crypto;

import org.junit.jupiter.api.Test;

import java.security.SecureRandom;
import java.util.Arrays;
import java.util.HexFormat;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Unit tests for {@link Cc2018Cipher}. The vectors below are not made up — {@code plaintext} was
 * fed to the real {@code libhellotalk-tea.so} native code (emulated with Unicorn, not
 * hand-transcribed from disassembly) to produce {@code payload}, so {@link Cc2018Cipher#decode}
 * is checked against ground truth. {@link Cc2018Cipher#encode}'s output was separately confirmed
 * to decrypt correctly under that same native-code emulation (see the reverse-engineer session
 * tooling under {@code re_output/tools/} for how these were generated) — that direction isn't
 * re-checked here since we have no native oracle inside this build, but the round-trip tests
 * below at least confirm encode/decode are mutually consistent.
 */
class Cc2018CipherTest {

    private static final HexFormat HEX = HexFormat.of();

    @Test
    void decodesOneByteVector() {
        byte[] plaintext = HEX.parseHex("61");
        byte[] payload = HEX.parseHex("fcf57e597795d72572dd10dfb4be653742ad678ad705d6ccd7f2f03e4f07c86e");

        assertArrayEquals(plaintext, Cc2018Cipher.decode(payload));
    }

    @Test
    void decodesSevenByteVector_justBelowBlockBoundary() {
        byte[] plaintext = HEX.parseHex("31323334353637");
        byte[] payload = HEX.parseHex("12d1109a5e6e4c04eba91d07d29c60b4aede4468ad78f92aba4698ef4d08536b04299d5e3a2a7af7");

        assertArrayEquals(plaintext, Cc2018Cipher.decode(payload));
    }

    @Test
    void decodesEightByteVector_exactBlockBoundary() {
        byte[] plaintext = HEX.parseHex("3132333435363738");
        byte[] payload = HEX.parseHex("e29fd032c6b134116cbabfb7d36e379e9f07302c11bb2ba84cf63b458ae97af07c4d2e9cb6d11716");

        assertArrayEquals(plaintext, Cc2018Cipher.decode(payload));
    }

    @Test
    void decodesRealisticJsonVector() {
        byte[] plaintext = HEX.parseHex(
            "7b22636f6465223a302c226d7367223a226f6b222c2264617461223a7b22757365725f6964223a"
            + "3136393333353536322c226e69636b6e616d65223a2274657374207573657220c3a9c3a8227d7d");
        byte[] payload = HEX.parseHex(
            "52b1b8429b56ad287d99c3b585830e2c76ccd6193f48e11ae01005e5afc4121110ed3d0cf2406d"
            + "052a29811eed32a712214d86df28679bc89ef090cca6200a1027a8fb9f7e64cc6b7c3629fb23b5f"
            + "13f9319423c5febf8c71846e16db44fcea7fd61f037ebffb0d3");

        assertArrayEquals(plaintext, Cc2018Cipher.decode(payload));
    }

    @Test
    void decodesSixtyFourByteRandomVector() {
        byte[] plaintext = HEX.parseHex(
            "2289785d08153b3ceb21a5ca5a5603724576e4f9fdabb744a769cacd2d9a5e271661e87ea0b6f8"
            + "09b8f92af18b7172e0f5aba3f27ff8a77d4dbaa347fddb40d8");
        byte[] payload = HEX.parseHex(
            "dd62b4834d71965d4cb24bc11476a1ffb61356e129d5bc77b1827f2822ee9030d56fab15479d1a"
            + "094a69ecf21bfab4bcd0e47d1cfe67f4c227a2999cb7b0b3f2848a9f3531f9972a586293a4bc2f9"
            + "1241ee9e7e70e54136f95edfcdbf25d386f");

        assertArrayEquals(plaintext, Cc2018Cipher.decode(payload));
    }

    @Test
    void encodeThenDecodeRoundTripsForVariousLengths() {
        SecureRandom random = new SecureRandom();
        for (int len : new int[] { 1, 7, 8, 9, 15, 16, 17, 63, 64, 65, 500 }) {
            byte[] plaintext = new byte[len];
            random.nextBytes(plaintext);

            byte[] payload = Cc2018Cipher.encode(plaintext);
            byte[] decoded = Cc2018Cipher.decode(payload);

            assertArrayEquals(plaintext, decoded, "round trip failed for length " + len);
        }
    }

    @Test
    void encodeProducesADifferentRandomKeyEveryTime() {
        byte[] plaintext = "same plaintext, different key each time".getBytes();

        byte[] payloadA = Cc2018Cipher.encode(plaintext);
        byte[] payloadB = Cc2018Cipher.encode(plaintext);

        byte[] keyA = Arrays.copyOfRange(payloadA, 0, 16);
        byte[] keyB = Arrays.copyOfRange(payloadB, 0, 16);
        org.junit.jupiter.api.Assertions.assertFalse(Arrays.equals(keyA, keyB));
    }

    @Test
    void rejectsEmptyPlaintext() {
        assertThrows(IllegalArgumentException.class, () -> Cc2018Cipher.encode(new byte[0]));
    }

    @Test
    void rejectsPayloadShorterThanKeyPlusOneBlock() {
        assertThrows(IllegalArgumentException.class, () -> Cc2018Cipher.decode(new byte[16]));
    }

    @Test
    void rejectsCiphertextLengthNotMultipleOfEight() {
        byte[] payload = new byte[16 + 9]; // key + 9-byte (non-block-aligned) ciphertext
        assertThrows(IllegalArgumentException.class, () -> Cc2018Cipher.decode(payload));
    }

    @Test
    void rejectsWrongKey() {
        byte[] plaintext = "some request body".getBytes();
        byte[] payload = Cc2018Cipher.encode(plaintext);
        byte[] tampered = payload.clone();
        tampered[0] ^= 0x01; // corrupt one byte of the embedded key

        assertThrows(IllegalStateException.class, () -> Cc2018Cipher.decode(tampered));
    }
}
