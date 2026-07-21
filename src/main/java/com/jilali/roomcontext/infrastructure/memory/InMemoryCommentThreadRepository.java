package com.jilali.roomcontext.infrastructure.memory;

import com.jilali.roomcontext.application.port.out.CommentThreadRepositoryPort;
import com.jilali.roomcontext.domain.model.RoomCommentThread;
import com.jilali.roomcontext.domain.valueobject.Cname;
import jakarta.inject.Singleton;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Singleton
public class InMemoryCommentThreadRepository implements CommentThreadRepositoryPort {

    private final Map<Cname, RoomCommentThread> store = new ConcurrentHashMap<>();

    @Override
    public RoomCommentThread findOrCreate(Cname cname) {
        return store.computeIfAbsent(cname, RoomCommentThread::new);
    }

    @Override
    public void save(RoomCommentThread thread) {
        store.put(thread.cname(), thread);
    }
}
