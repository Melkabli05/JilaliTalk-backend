package com.jilali.roomcontext.domain.valueobject;

/**
 * The room's business type, as HelloTalk's upstream API encodes it — {@code 1} for a "voice"
 * room, {@code 2} for a "live" room, inferred from the legacy controller's {@code /voice/*}
 * vs {@code /live/*} path segments.
 *
 * <p>Deliberately kept as a value object wrapping {@code int} rather than promoted to an enum
 * or sealed type: the audit never confirmed the complete upstream value set beyond these two
 * observed values, and an exhaustive closed type would silently reject a valid future upstream
 * value instead of degrading gracefully. See {@code docs/room-redesign/09-technical-risks.md}
 * (R1) for the plan to revisit this once the full value set is confirmed against the
 * decompiled APK.
 */
public record BusiType(int value) {

    public static final BusiType VOICE = new BusiType(1);
    public static final BusiType LIVE = new BusiType(2);

    public BusiType {
        if (value <= 0) {
            throw new IllegalArgumentException("busiType must be positive, got " + value);
        }
    }

    public boolean isVoice() {
        return value == VOICE.value;
    }

    public boolean isLive() {
        return value == LIVE.value;
    }
}
