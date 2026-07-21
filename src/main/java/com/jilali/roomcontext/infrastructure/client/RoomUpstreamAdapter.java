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
    private final JilaliProperties properties;

    public RoomUpstreamAdapter(RoomJilaliClient client, JilaliProperties properties) {
        this.client = client;
        this.properties = properties;
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

    @Override
    public VoiceRoomInfoResponse voiceRoomInfoRaw(String cname) {
        return UpstreamRetry.withRetry(() -> JilaliResponses.unwrap(client.voiceRoomInfo(cname)));
    }

    @Override
    public VoiceRoomInfoResponse liveRoomInfoRaw(String cname) {
        return UpstreamRetry.withRetry(() -> JilaliResponses.unwrap(client.liveRoomInfo(cname)));
    }

    @Override
    public VoiceRoomInfoResponse decryptRtcToken(VoiceRoomInfoResponse resp) {
        if (resp == null || resp.channelInfo() == null) {
            return resp;
        }
        if (resp.channelInfo().rtcInfo() == null) {
            throw new JilaliException("Upstream returned no RTC info for channel", HttpStatus.BAD_GATEWAY);
        }
        var encrypted = resp.channelInfo().rtcInfo().token();
        byte[] key = properties.agoraCipherKey().getBytes(StandardCharsets.US_ASCII);
        return resp.withRtcToken(AgoraTokenCipher.decrypt(encrypted, key));
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
