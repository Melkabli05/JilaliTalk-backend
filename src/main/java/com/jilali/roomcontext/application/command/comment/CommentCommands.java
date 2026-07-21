package com.jilali.roomcontext.application.command.comment;

import com.jilali.roomcontext.domain.valueobject.Cname;
import com.jilali.roomcontext.domain.valueobject.RoomUserId;

import java.util.Optional;

public final class CommentCommands {
    private CommentCommands() {}

    public record PostCommentCommand(Cname cname, RoomUserId author, String text, Optional<String> replyToMessageId) {}
    public record SwitchCaptionLanguageCommand(Cname cname, String languageCode) {}
}
