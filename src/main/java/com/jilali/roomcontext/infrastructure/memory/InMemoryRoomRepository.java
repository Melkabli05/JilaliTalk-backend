package com.jilali.roomcontext.infrastructure.memory;

import com.jilali.roomcontext.application.port.out.RoomRepositoryPort;
import com.jilali.roomcontext.domain.model.Room;
import com.jilali.roomcontext.domain.valueobject.Cname;
import jakarta.inject.Singleton;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/** Phase 2 placeholder: HelloTalk is the real system of record for Room state (see
 *  docs/room-redesign/01-domain-model.md). This in-memory store is replaced by a real
 *  upstream-backed adapter in Phase 3 (docs/room-redesign/07-migration-roadmap.md). */
@Singleton
public class InMemoryRoomRepository implements RoomRepositoryPort {

    private final Map<Cname, Room> store = new ConcurrentHashMap<>();

    @Override
    public Optional<Room> findByCname(Cname cname) {
        return Optional.ofNullable(store.get(cname));
    }

    @Override
    public void save(Room room) {
        store.put(room.cname(), room);
    }
}
