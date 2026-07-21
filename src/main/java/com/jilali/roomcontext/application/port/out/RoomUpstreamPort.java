package com.jilali.roomcontext.application.port.out;

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

import java.util.List;
import java.util.Map;

public interface RoomUpstreamPort {
    ChannelListResponse listVoiceRooms(int langId, int limit, int offset, int refresh);
    ChannelListResponse listLiveRooms(int langId, int limit, int offset, int refresh);
    ChannelListResponse recommendVoiceRooms(String excludeCname, String scene);
    ChannelListResponse recommendLiveRooms(String scene);
    ChannelListItem recommendSingleVoiceRoom(int langId);
    List<LanguageGroup> languageGroupsVoice(String scene);
    List<LanguageGroup> languageGroupsLive();
    CategoryTopicListResponse categories(int busiType);
    VoiceRoomInfoResponse voiceRoomInfoRaw(String cname);
    VoiceRoomInfoResponse liveRoomInfoRaw(String cname);
    VoiceRoomInfoResponse decryptRtcToken(VoiceRoomInfoResponse response);
    Map<String, Object> channelBasicInfo(String cname);
    BatchQueryResponse batchQuery(BatchQueryRequest request);
    Map<String, Object> liveVoiceConfig();
    CreateVoiceChannelResponse createVoiceChannel(CreateVoiceChannelRequest request);
    void updateVoiceChannel(UpdateVoiceChannelRequest request);
    Map<String, Object> endChannel(EndChannelRequest request);
    Map<String, Object> activeChannel(int busiType);
    Map<String, Object> latestSettings(int busiType);
}
