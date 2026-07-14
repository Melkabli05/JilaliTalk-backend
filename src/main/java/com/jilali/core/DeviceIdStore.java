package com.jilali.core;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.SecureRandom;
import java.util.UUID;

/**
 * Loads or creates the device id this BFF impersonates, persisted so it stays stable across
 * restarts — mirroring the real app's own behavior. The real app ({@code DeviceVQHelper
 * .generateDVId()}, see {@code re_output/FINDINGS.md} §7 device-id notes) derives its device id
 * from {@code Settings.Secure.ANDROID_ID} plus a native-computed value
 * ({@code TeaUtils.xInitValue}), runs it through {@code UUID.nameUUIDFromBytes} (an MD5-based,
 * deterministic type-3 UUID), strips the dashes, and persists the 32-char hex result in MMKV
 * under key {@code .android_dvd} — generated once per install, reused forever after.
 * <p>
 * This BFF has neither a real ANDROID_ID nor the native {@code xInitValue} routine to seed from
 * (it's a server process, not an Android device), so the seed here is random rather than
 * derived from real device material. What's preserved is the part that actually matters for
 * looking like a consistent returning device to the upstream server: the wire shape (32
 * lowercase hex characters, MD5-UUID-derived) and — critically — persistence. A fresh random
 * device id on every process restart (the previous behavior) would make every restart look like
 * a brand-new device to HelloTalk's anti-fraud stack, which is exactly the failure mode real
 * persistence avoids.
 */
public final class DeviceIdStore {

    private static final Path FILE = Path.of("./data/device_id");

    private DeviceIdStore() {}

    /** Returns the persisted device id, creating and persisting one on first call. */
    public static String loadOrCreate() {
        try {
            if (Files.exists(FILE)) {
                String existing = Files.readString(FILE).strip();
                if (!existing.isBlank()) {
                    return existing;
                }
            }
            String generated = generate();
            if (FILE.getParent() != null) {
                Files.createDirectories(FILE.getParent());
            }
            Files.writeString(FILE, generated);
            return generated;
        } catch (IOException e) {
            // A missing/unwritable data directory shouldn't fail startup over a device id — it
            // just won't survive this particular restart, degrading to the old per-process
            // behavior rather than crashing.
            return generate();
        }
    }

    private static String generate() {
        byte[] seed = new byte[16];
        new SecureRandom().nextBytes(seed);
        return UUID.nameUUIDFromBytes(seed).toString().replace("-", "");
    }
}
