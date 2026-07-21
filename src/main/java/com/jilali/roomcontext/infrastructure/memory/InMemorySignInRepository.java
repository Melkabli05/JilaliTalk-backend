package com.jilali.roomcontext.infrastructure.memory;

import com.jilali.roomcontext.application.port.out.SignInRepositoryPort;
import com.jilali.roomcontext.domain.model.RoomSignIn;
import com.jilali.roomcontext.domain.valueobject.Cname;
import com.jilali.roomcontext.domain.valueobject.RoomUserId;
import jakarta.inject.Singleton;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

@Singleton
public class InMemorySignInRepository implements SignInRepositoryPort {

    private record Key(RoomUserId userId, Cname cname) {}

    private final Map<Key, RoomSignIn> store = new ConcurrentHashMap<>();

    @Override
    public Optional<RoomSignIn> find(RoomUserId userId, Cname cname) {
        return Optional.ofNullable(store.get(new Key(userId, cname)));
    }

    @Override
    public void save(RoomSignIn signIn) {
        store.put(new Key(signIn.userId(), signIn.cname()), signIn);
    }
}
