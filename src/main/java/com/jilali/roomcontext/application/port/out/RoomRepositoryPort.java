package com.jilali.roomcontext.application.port.out;

import com.jilali.roomcontext.domain.model.Room;
import com.jilali.roomcontext.domain.valueobject.Cname;

import java.util.Optional;

/** Upstream-facing port — there is no local database backing this bounded context; HelloTalk's
 *  own backend is the system of record. An implementation of this port reconstructs a Room
 *  aggregate from live upstream calls (or, for Phase 2, an in-memory fake). */
public interface RoomRepositoryPort {
    Optional<Room> findByCname(Cname cname);
    void save(Room room);
}
