package com.jilali.roomcontext.domain.valueobject;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MicStateTest {

    @Test
    void offAndPendingApprovalAndListeningCarryNoData() {
        MicState off = new MicState.Off();
        MicState pending = new MicState.PendingApproval();
        MicState listening = new MicState.Listening();
        assertEquals(new MicState.Off(), off);
        assertEquals(new MicState.PendingApproval(), pending);
        assertEquals(new MicState.Listening(), listening);
    }

    @Test
    void speakingCarriesCameraState() {
        MicState speakingCamOn = new MicState.Speaking(true);
        MicState speakingCamOff = new MicState.Speaking(false);
        assertTrue(speakingCamOn instanceof MicState.Speaking(boolean camOn) && camOn);
        assertTrue(speakingCamOff instanceof MicState.Speaking(boolean camOn) && !camOn);
    }

    @Test
    void patternMatchingSwitchCoversAllCases() {
        MicState state = new MicState.Speaking(true);
        String description = switch (state) {
            case MicState.Off ignored -> "off";
            case MicState.PendingApproval ignored -> "pending";
            case MicState.Listening ignored -> "listening";
            case MicState.Speaking(boolean camOn) -> camOn ? "speaking-cam-on" : "speaking-cam-off";
        };
        assertEquals("speaking-cam-on", description);
    }
}
