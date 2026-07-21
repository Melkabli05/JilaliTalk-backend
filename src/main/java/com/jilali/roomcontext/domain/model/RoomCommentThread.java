package com.jilali.roomcontext.domain.model;

import com.jilali.roomcontext.domain.valueobject.Cname;
import com.jilali.roomcontext.domain.valueobject.RoomUserId;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public final class RoomCommentThread {

    private final Cname cname;
    private final List<Comment> comments = new ArrayList<>();

    public RoomCommentThread(Cname cname) {
        this.cname = cname;
    }

    public Cname cname() {
        return cname;
    }

    public Comment post(RoomUserId author, String text, Optional<Comment.ReplyTarget> replyTo) {
        Comment comment = new Comment(UUID.randomUUID().toString(), author, text,
            System.currentTimeMillis(), replyTo);
        comments.add(comment);
        return comment;
    }

    public List<Comment> comments() {
        return List.copyOf(comments);
    }
}
