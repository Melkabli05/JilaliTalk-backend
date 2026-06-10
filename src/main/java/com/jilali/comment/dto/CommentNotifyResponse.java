package com.jilali.comment.dto;

import io.micronaut.core.annotation.Nullable;
import io.micronaut.serde.annotation.Serdeable;

import java.util.List;

@Serdeable
public record CommentNotifyResponse(@Nullable List<CommentNotify> items) {
    public List<CommentNotify> items() {
        return items == null ? List.of() : items;
    }
}
