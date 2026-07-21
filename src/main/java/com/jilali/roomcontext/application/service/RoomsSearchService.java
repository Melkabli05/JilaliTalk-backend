package com.jilali.roomcontext.application.service;

import com.jilali.roomcontext.infrastructure.client.JilaliResponses;
import com.jilali.roomcontext.infrastructure.client.RoomJilaliClient;
import com.jilali.roomcontext.infrastructure.client.TextMatcher;
import com.jilali.roomcontext.infrastructure.dto.room.ChannelListItem;
import com.jilali.roomcontext.infrastructure.dto.room.ChannelListResponse;
import jakarta.inject.Singleton;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.StructuredTaskScope;

/** Native reimplementation of the legacy room.RoomsSearchService - fans out up to maxPages
 *  concurrent list-room calls and filters with TextMatcher. Zero dependency on
 *  client.JilaliClient/client.JilaliResponses. */
@Singleton
public class RoomsSearchService {

    private static final int PAGE_SIZE = 20;

    private final RoomJilaliClient client;

    public RoomsSearchService(RoomJilaliClient client) {
        this.client = client;
    }

    public ChannelListResponse search(String type, String query, int langId, int maxPages) {
        boolean isLive = "live".equals(type);
        try (var scope = StructuredTaskScope.open()) {
            var tasks = new ArrayList<StructuredTaskScope.Subtask<ChannelListResponse>>();
            for (int page = 0; page < maxPages; page++) {
                int offset = page * PAGE_SIZE;
                tasks.add(scope.fork(() -> JilaliResponses.unwrap(
                        isLive
                                ? client.listLiveRooms(langId, PAGE_SIZE, offset, 1)
                                : client.listVoiceRooms(langId, PAGE_SIZE, offset, 1))));
            }

            scope.join();

            List<ChannelListItem> matched = new ArrayList<>();
            for (var task : tasks) {
                for (ChannelListItem item : task.get().items()) {
                    if (matchesQuery(item, query)) {
                        matched.add(item);
                    }
                }
            }
            return new ChannelListResponse(matched);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("Interrupted during rooms search", e);
        } catch (StructuredTaskScope.FailedException e) {
            throw new RuntimeException("Upstream fetch failed during rooms search", e.getCause());
        }
    }

    static boolean matchesQuery(ChannelListItem item, String query) {
        List<String> haystacks = new ArrayList<>();
        haystacks.add(item.channel().name());
        haystacks.add(item.channel().cname());
        haystacks.add(item.channel().description());
        haystacks.add(item.hostUser().nickname());
        if (item.categoryTopicTag() != null) {
            haystacks.add(item.categoryTopicTag().categoryName());
            if (item.categoryTopicTag().topicName() != null) {
                haystacks.add(item.categoryTopicTag().topicName());
            }
        }
        if (item.users() != null) {
            for (var user : item.users()) {
                haystacks.add(user.nickname());
            }
        }
        return TextMatcher.matches(haystacks, query);
    }
}
