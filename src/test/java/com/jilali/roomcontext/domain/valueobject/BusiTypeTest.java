package com.jilali.roomcontext.domain.valueobject;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BusiTypeTest {

    @Test
    void rejectsNonPositive() {
        assertThrows(IllegalArgumentException.class, () -> new BusiType(0));
        assertThrows(IllegalArgumentException.class, () -> new BusiType(-1));
    }

    @Test
    void voiceConstantIsVoice() {
        assertTrue(BusiType.VOICE.isVoice());
        assertFalse(BusiType.VOICE.isLive());
    }

    @Test
    void liveConstantIsLive() {
        assertTrue(BusiType.LIVE.isLive());
        assertFalse(BusiType.LIVE.isVoice());
    }

    @Test
    void unknownValueIsNeitherVoiceNorLive() {
        BusiType unknown = new BusiType(3);
        assertFalse(unknown.isVoice());
        assertFalse(unknown.isLive());
    }
}
