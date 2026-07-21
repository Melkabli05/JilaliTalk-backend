package com.jilali.roomcontext.domain.model;

import com.jilali.roomcontext.domain.valueobject.RoomUserId;

import java.util.Optional;

public record Comment(
        String id,
        RoomUserId authorId,
        String text,
        long createdAtMillis,
        Optional<ReplyTarget> replyTo) {

    public record ReplyTarget(String messageId, RoomUserId fromUserId, String text) {}
}
