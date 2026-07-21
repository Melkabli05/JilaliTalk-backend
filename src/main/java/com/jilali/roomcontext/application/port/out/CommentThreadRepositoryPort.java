package com.jilali.roomcontext.application.port.out;

import com.jilali.roomcontext.domain.model.RoomCommentThread;
import com.jilali.roomcontext.domain.valueobject.Cname;

public interface CommentThreadRepositoryPort {
    RoomCommentThread findOrCreate(Cname cname);
    void save(RoomCommentThread thread);
}
