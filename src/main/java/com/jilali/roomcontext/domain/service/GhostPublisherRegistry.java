package com.jilali.roomcontext.domain.service;

import jakarta.inject.Singleton;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * In-memory tracking of which users are currently "ghost publishing" (speaking from the
 * audience while staying invisible in the roster) in a given room. Purely a BFF-local
 * concept — upstream (HelloTalk's livehub) has no idea this exists, since the whole point
 * is to never call upstream's join/quit for these users (that would add them to the
 * audience roster, defeating the invisibility).
 *
 * <p>Deliberately not persisted anywhere: a server restart, or a client that joins the
 * room after a ghost publisher started speaking, simply won't see them until the next
 * start/stop toggle. This is a known, accepted limitation of the v1 BFF-mediated
 * implementation — see GhostPublishController's class doc for the full picture.
 */
@Singleton
public class GhostPublisherRegistry {

    private final ConcurrentHashMap<String, Set<Long>> byRoom = new ConcurrentHashMap<>();

    /** Returns true if this call actually started ghost-publishing (i.e. the user wasn't
     *  already registered) — false means it was a no-op (idempotent double-start). */
    public boolean start(String cname, long userId) {
        Set<Long> users = byRoom.computeIfAbsent(cname, k -> ConcurrentHashMap.newKeySet());
        return users.add(userId);
    }

    /** Returns true if this call actually stopped ghost-publishing — false means the user
     *  wasn't registered as one (idempotent double-stop, or stopping without starting). */
    public boolean stop(String cname, long userId) {
        Set<Long> users = byRoom.get(cname);
        if (users == null) return false;
        boolean removed = users.remove(userId);
        if (users.isEmpty()) byRoom.remove(cname, users);
        return removed;
    }

    public boolean isGhostPublishing(String cname, long userId) {
        Set<Long> users = byRoom.get(cname);
        return users != null && users.contains(userId);
    }
}
