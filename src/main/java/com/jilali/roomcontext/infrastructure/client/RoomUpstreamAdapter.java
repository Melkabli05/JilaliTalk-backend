package com.jilali.roomcontext.infrastructure.client;

import com.jilali.core.JilaliException;
import com.jilali.core.JilaliProperties;
import com.jilali.roomcontext.application.port.out.RoomUpstreamPort;
import com.jilali.roomcontext.infrastructure.crypto.AgoraTokenCipher;
import com.jilali.roomcontext.infrastructure.dto.room.BatchQueryRequest;
import com.jilali.roomcontext.infrastructure.dto.room.BatchQueryResponse;
import com.jilali.roomcontext.infrastructure.dto.room.CategoryTopicListResponse;
import com.jilali.roomcontext.infrastructure.dto.room.ChannelListItem;
import com.jilali.roomcontext.infrastructure.dto.room.ChannelListResponse;
import com.jilali.roomcontext.infrastructure.dto.room.CreateVoiceChannelRequest;
import com.jilali.roomcontext.infrastructure.dto.room.CreateVoiceChannelResponse;
import com.jilali.roomcontext.infrastructure.dto.room.EndChannelRequest;
import com.jilali.roomcontext.infrastructure.dto.room.LanguageGroup;
import com.jilali.roomcontext.infrastructure.dto.room.UpdateVoiceChannelRequest;
import com.jilali.roomcontext.infrastructure.dto.room.VoiceRoomInfoResponse;
import io.micronaut.http.HttpStatus;
import jakarta.inject.Singleton;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

/** Dedicated Room discovery/info/lifecycle upstream adapter - zero dependency on the legacy
 *  client.JilaliClient god interface. */
@Singleton
public class RoomUpstreamAdapter implements RoomUpstreamPort {

    private final RoomJilaliClient client;
    private final byte[] agoraCipherKey;

    public RoomUpstreamAdapter(RoomJilaliClient client, JilaliProperties properties) {
        this.client = client;
        this.agoraCipherKey = properties.agoraCipherKey().getBytes(StandardCharsets.US_ASCII);
    }

    @Override
    public ChannelListResponse listVoiceRooms(int langId, int limit, int offset, int refresh) {
        return JilaliResponses.unwrap(client.listVoiceRooms(langId, limit, offset, refresh));
    }

    @Override
    public ChannelListResponse listLiveRooms(int langId, int limit, int offset, int refresh) {
        return JilaliResponses.unwrap(client.listLiveRooms(langId, limit, offset, refresh));
    }

    @Override
    public ChannelListResponse recommendVoiceRooms(String excludeCname, String scene) {
        return JilaliResponses.unwrap(client.recommendVoiceRooms(
            excludeCname == null || excludeCname.isBlank() ? null : excludeCname, scene));
    }

    @Override
    public ChannelListResponse recommendLiveRooms(String scene) {
        return JilaliResponses.unwrap(client.recommendLiveRooms(scene));
    }

    @Override
    public ChannelListItem recommendSingleVoiceRoom(int langId) {
        return JilaliResponses.unwrap(client.recommendSingleVoiceRoom(langId));
    }

    @Override
    public List<LanguageGroup> languageGroupsVoice(String scene) {
        return JilaliResponses.unwrap(client.languageGroupVoice(scene));
    }

    @Override
    public List<LanguageGroup> languageGroupsLive() {
        return JilaliResponses.unwrap(client.languageGroupLive());
    }

    @Override
    public CategoryTopicListResponse categories(int busiType) {
        return JilaliResponses.unwrap(client.categoryTopicList(busiType));
    }

    /** A fresh-room 5xx on upstream's voice_room_info is recoverable with a short wait, not a
     *  fatal "Room not found" - the frontend's create-room flow hits this single-call endpoint
     *  directly via {@code fresh=true}, so without this retry a freshly created room's first
     *  viewport load 500s and bounces the user back to home with a wrong "please create a new
     *  one" message. */
    @Override
    public VoiceRoomInfoResponse voiceRoomInfoRaw(String cname) {
        return UpstreamRetry.withRetry(() -> JilaliResponses.unwrap(client.voiceRoomInfo(cname)));
    }

    /** Same fresh-room retry note as {@link #voiceRoomInfoRaw} (this is the live/video-room
     *  counterpart). */
    @Override
    public VoiceRoomInfoResponse liveRoomInfoRaw(String cname) {
        return UpstreamRetry.withRetry(() -> JilaliResponses.unwrap(client.liveRoomInfo(cname)));
    }

    /** LiveHub hands back an AES-encrypted {@code rtc_info.token}; the browser Agora SDK needs
     *  the plain token (it carries the App ID) or the gateway reports
     *  {@code CAN_NOT_GET_GATEWAY_SERVER: invalid vendor key, can not find appid}. */
    @Override
    public VoiceRoomInfoResponse decryptRtcToken(VoiceRoomInfoResponse resp) {
        if (resp == null || resp.channelInfo() == null) {
            return resp;
        }
        if (resp.channelInfo().rtcInfo() == null) {
            throw new JilaliException("Upstream returned no RTC info for channel", HttpStatus.BAD_GATEWAY);
        }
        var encrypted = resp.channelInfo().rtcInfo().token();
        return resp.withRtcToken(AgoraTokenCipher.decrypt(encrypted, agoraCipherKey));
    }

    @Override
    public Map<String, Object> channelBasicInfo(String cname) {
        return JilaliResponses.unwrap(client.channelBasicInfo(cname));
    }

    @Override
    public BatchQueryResponse batchQuery(BatchQueryRequest request) {
        return JilaliResponses.unwrap(client.batchQueryChannel(request));
    }

    @Override
    public Map<String, Object> liveVoiceConfig() {
        return JilaliResponses.unwrap(client.liveVoiceConfig());
    }

    @Override
    public CreateVoiceChannelResponse createVoiceChannel(CreateVoiceChannelRequest request) {
        return JilaliResponses.unwrap(client.createVoiceChannel(request));
    }

    @Override
    public void updateVoiceChannel(UpdateVoiceChannelRequest request) {
        JilaliResponses.unwrap(client.updateVoiceChannel(request));
    }

    @Override
    public Map<String, Object> endChannel(EndChannelRequest request) {
        return JilaliResponses.unwrap(client.endChannel(request));
    }

    @Override
    public Map<String, Object> activeChannel(int busiType) {
        var resp = client.userStartedChannel(busiType);
        return resp == null ? null : resp.data();
    }

    @Override
    public Map<String, Object> latestSettings(int busiType) {
        return JilaliResponses.unwrap(client.userLatestChannel(busiType));
    }
}
