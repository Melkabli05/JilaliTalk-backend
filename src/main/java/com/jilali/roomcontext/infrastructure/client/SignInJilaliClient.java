package com.jilali.roomcontext.infrastructure.client;

import com.jilali.core.JilaliEnvelope;
import com.jilali.roomcontext.infrastructure.dto.room.RoomLevelConfigResponse;
import com.jilali.roomcontext.infrastructure.dto.signin.ClaimRewardRequest;
import com.jilali.roomcontext.infrastructure.dto.signin.ClaimTaskRewardRequest;
import com.jilali.roomcontext.infrastructure.dto.signin.RoomLevelRewardResponse;
import com.jilali.roomcontext.infrastructure.dto.signin.VoiceSignPanelResponse;
import com.jilali.roomcontext.infrastructure.dto.signin.VoiceTasksResponse;
import io.micronaut.http.annotation.Body;
import io.micronaut.http.annotation.Get;
import io.micronaut.http.annotation.Post;
import io.micronaut.http.annotation.QueryValue;
import io.micronaut.http.client.annotation.Client;

/** Dedicated Sign-in / daily-reward upstream client - calls HelloTalk's
 *  {@code /livehub/voice*} endpoints directly. Zero dependency on the legacy
 *  client.JilaliClient god interface. */
@Client(id = "jlhub", path = "/livehub")
public interface SignInJilaliClient {

    @Get("/voice_sign_panel")
    JilaliEnvelope<VoiceSignPanelResponse> voiceSignPanel(@QueryValue String cname);

    @Get("/voice/tasks")
    JilaliEnvelope<VoiceTasksResponse> voiceTasks();

    @Post("/voice/task/reward")
    JilaliEnvelope<Object> voiceTaskReward(@Body ClaimTaskRewardRequest body);

    @Get("/voice/room_level/reward")
    JilaliEnvelope<RoomLevelRewardResponse> roomLevelReward(
        @QueryValue String cname, @QueryValue("host_id") long hostId, @QueryValue int level);

    @Post("/voice/room_level/reward")
    JilaliEnvelope<Object> claimRoomLevelReward(@Body ClaimRewardRequest body);

    @Get("/voice/room_level/config")
    JilaliEnvelope<RoomLevelConfigResponse> roomLevelConfig(
        @QueryValue String cname, @QueryValue("host_id") long hostId);
}
