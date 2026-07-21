package com.jilali.roomcontext.infrastructure.client;

import com.jilali.client.JilaliClient;
import com.jilali.client.JilaliResponses;
import com.jilali.core.JilaliException;
import com.jilali.core.JilaliProperties;
import com.jilali.room.AgoraTokenCipher;
import com.jilali.room.dto.BatchQueryRequest;
import com.jilali.room.dto.BatchQueryResponse;
import com.jilali.room.dto.CategoryTopicListResponse;
import com.jilali.room.dto.ChannelListItem;
import com.jilali.room.dto.ChannelListResponse;
import com.jilali.room.dto.CreateVoiceChannelRequest;
import com.jilali.room.dto.CreateVoiceChannelResponse;
import com.jilali.room.dto.EndChannelRequest;
import com.jilali.room.dto.LanguageGroup;
import com.jilali.room.dto.UpdateVoiceChannelRequest;
import com.jilali.room.dto.VoiceRoomInfoResponse;
import com.jilali.roomcontext.application.port.out.RoomUpstreamPort;
import io.micronaut.http.HttpStatus;
import io.micronaut.http.client.exceptions.HttpClientResponseException;
import jakarta.inject.Singleton;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;

/** Wraps client.JilaliClient for the pure pass-through room discovery/info/lifecycle calls.
 *  The retry-on-5xx helper mirrors RoomJoinService.withUpstreamRetry exactly (same 4-attempt,
 *  700ms-delay shape) - reimplemented here rather than reused because that method is
 *  package-private to com.jilali.room and the migration strategy requires legacy files stay
 *  untouched, so making it public there isn't an option. */
@Singleton
public class LegacyRoomUpstreamAdapter implements RoomUpstreamPort {

    private static final Logger log = LoggerFactory.getLogger(LegacyRoomUpstreamAdapter.class);
    private static final int MAX_UPSTREAM_ATTEMPTS = 4;
    private static final Duration UPSTREAM_RETRY_DELAY = Duration.ofMillis(700);

    private final JilaliClient client;
    private final JilaliProperties properties;

    public LegacyRoomUpstreamAdapter(JilaliClient client, JilaliProperties properties) {
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
        return withUpstreamRetry(() -> JilaliResponses.unwrap(client.voiceRoomInfo(cname)));
    }

    @Override
    public VoiceRoomInfoResponse liveRoomInfoRaw(String cname) {
        return withUpstreamRetry(() -> JilaliResponses.unwrap(client.liveRoomInfo(cname)));
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

    private <T> T withUpstreamRetry(Callable<T> call) {
        for (int attempt = 1; ; attempt++) {
            try {
                return call.call();
            } catch (HttpClientResponseException e) {
                boolean serverError = e.getStatus().getCode() >= 500;
                String upstreamBody = e.getResponse() != null
                    ? e.getResponse().getBody(String.class).orElse("<empty>") : "<no response>";
                if (!serverError || attempt >= MAX_UPSTREAM_ATTEMPTS) {
                    log.warn("Upstream call failed permanently (status={}): {}", e.getStatus(), upstreamBody);
                    throw e;
                }
                log.warn("Upstream call failed (status={}), retrying attempt {}/{}: {}",
                    e.getStatus(), attempt, MAX_UPSTREAM_ATTEMPTS - 1, upstreamBody);
                try {
                    Thread.sleep(UPSTREAM_RETRY_DELAY.toMillis());
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    throw new RuntimeException("Interrupted during upstream retry", ie);
                }
            } catch (Exception e) {
                throw new RuntimeException("Upstream call failed", e);
            }
        }
    }
}
