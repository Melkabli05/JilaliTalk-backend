package com.jilali.room;

import com.jilali.client.JilaliClient;
import com.jilali.client.JilaliResponses;
import com.jilali.room.dto.ChannelListItem;
import com.jilali.room.dto.ChannelListResponse;
import jakarta.inject.Singleton;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.StructuredTaskScope;

/**
 * Bounded server-side room search: fans out up to {@code maxPages} concurrent list-room calls
 * and filters the combined result with {@link TextMatcher}. Not a full-corpus search — upstream
 * has no keyword parameter (confirmed absent from {@link JilaliClient} and every captured
 * request in websocket_realtime.md). Replaces the frontend's up-to-10-sequential-round-trips
 * auto-paginate-while-searching loop with one parallel server-side fan-out, same result ceiling.
 * Mirrors {@code RoomJoinService}'s Structured Concurrency pattern.
 */
@Singleton
public class RoomsSearchService {

    private static final int PAGE_SIZE = 20;

    private final JilaliClient client;

    public RoomsSearchService(JilaliClient client) {
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
