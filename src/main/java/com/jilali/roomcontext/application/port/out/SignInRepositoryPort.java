package com.jilali.roomcontext.application.port.out;

import com.jilali.roomcontext.domain.model.RoomSignIn;
import com.jilali.roomcontext.domain.valueobject.Cname;
import com.jilali.roomcontext.domain.valueobject.RoomUserId;

import java.util.Optional;

public interface SignInRepositoryPort {
    Optional<RoomSignIn> find(RoomUserId userId, Cname cname);
    void save(RoomSignIn signIn);
}
