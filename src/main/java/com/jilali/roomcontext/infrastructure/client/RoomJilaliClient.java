package com.jilali.roomcontext.infrastructure.client;

import com.jilali.core.JilaliEnvelope;
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
import io.micronaut.core.annotation.Nullable;
import io.micronaut.http.annotation.Body;
import io.micronaut.http.annotation.Get;
import io.micronaut.http.annotation.Post;
import io.micronaut.http.annotation.QueryValue;
import io.micronaut.http.client.annotation.Client;

import java.util.List;
import java.util.Map;

/** Dedicated Room discovery/info/lifecycle upstream client - calls HelloTalk's
 *  {@code /livehub/*} room endpoints directly. Zero dependency on the legacy
 *  client.JilaliClient god interface. */
@Client(id = "jlhub", path = "/livehub")
public interface RoomJilaliClient {

    @Get("/channel_list/voice")
    JilaliEnvelope<ChannelListResponse> listVoiceRooms(@QueryValue("lang_id") int langId,
                                                        @QueryValue int limit, @QueryValue int offset,
                                                        @QueryValue int refresh);

    @Get("/channel_list/live")
    JilaliEnvelope<ChannelListResponse> listLiveRooms(@QueryValue("lang_id") int langId,
                                                       @QueryValue int limit, @QueryValue int offset,
                                                       @QueryValue int refresh);

    @Get("/channel_list_recommend/voice")
    JilaliEnvelope<ChannelListResponse> recommendVoiceRooms(@QueryValue @Nullable String excludeCname,
                                                             @QueryValue String scene);

    @Get("/channel_list_recommend/live")
    JilaliEnvelope<ChannelListResponse> recommendLiveRooms(@QueryValue String scene);

    @Get("/channel_recommend/voice")
    JilaliEnvelope<ChannelListItem> recommendSingleVoiceRoom(@QueryValue("lang_id") int langId);

    @Get("/language_group/voice")
    JilaliEnvelope<List<LanguageGroup>> languageGroupVoice(@QueryValue String scene);

    @Get("/language_group/live")
    JilaliEnvelope<List<LanguageGroup>> languageGroupLive();

    @Get("/category_topic_list")
    JilaliEnvelope<CategoryTopicListResponse> categoryTopicList(@QueryValue("busi_type") int busiType);

    @Get("/voice_room_info")
    JilaliEnvelope<VoiceRoomInfoResponse> voiceRoomInfo(@QueryValue String cname);

    @Get("/live_room_info")
    JilaliEnvelope<VoiceRoomInfoResponse> liveRoomInfo(@QueryValue String cname);

    @Get("/channel_basic_info")
    JilaliEnvelope<Map<String, Object>> channelBasicInfo(@QueryValue String cname);

    @Post("/batch_query_channel")
    JilaliEnvelope<BatchQueryResponse> batchQueryChannel(@Body BatchQueryRequest body);

    @Get("/live_voice/cfg")
    JilaliEnvelope<Map<String, Object>> liveVoiceConfig();

    @Post("/create_voice_channel")
    JilaliEnvelope<CreateVoiceChannelResponse> createVoiceChannel(@Body CreateVoiceChannelRequest body);

    @Post("/update_voice_channel")
    JilaliEnvelope<Object> updateVoiceChannel(@Body UpdateVoiceChannelRequest body);

    @Post("/end_channel")
    JilaliEnvelope<Map<String, Object>> endChannel(@Body EndChannelRequest body);

    @Get("/user_started_channel")
    @Nullable
    JilaliEnvelope<Map<String, Object>> userStartedChannel(@QueryValue("busi_type") int busiType);

    @Get("/user_latest_channel")
    JilaliEnvelope<Map<String, Object>> userLatestChannel(@QueryValue("busi_type") int busiType);
}
