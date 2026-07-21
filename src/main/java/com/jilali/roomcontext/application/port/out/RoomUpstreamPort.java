package com.jilali.roomcontext.application.port.out;

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

import java.util.List;
import java.util.Map;

/** Pass-through discovery/info/lifecycle calls - the fan-out (join-bundle) and drift-correction
 *  (audience-reconcile) and search behaviors are deliberately NOT here, since the existing
 *  legacy RoomJoinService/RoomEventSource/RoomsSearchService already correctly implement them
 *  and are reused directly by api.RoomController rather than re-wrapped - see that class's
 *  Javadoc. */
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
