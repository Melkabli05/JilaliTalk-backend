package com.jilali.roomcontext.application.service;

import com.jilali.roomcontext.application.command.comment.CommentCommands.PostCommentCommand;
import com.jilali.roomcontext.application.port.in.CommentUseCases;
import com.jilali.roomcontext.application.port.out.CommentThreadRepositoryPort;
import com.jilali.roomcontext.application.port.out.RoomEventPublisherPort;
import com.jilali.roomcontext.domain.event.RoomEvent;
import com.jilali.roomcontext.domain.model.Comment;
import com.jilali.roomcontext.domain.model.RoomCommentThread;
import com.jilali.roomcontext.domain.valueobject.Cname;

import jakarta.inject.Singleton;

import java.util.List;
import java.util.Optional;

@Singleton
public class CommentService implements CommentUseCases {

    private final CommentThreadRepositoryPort threads;
    private final RoomEventPublisherPort events;

    public CommentService(CommentThreadRepositoryPort threads, RoomEventPublisherPort events) {
        this.threads = threads;
        this.events = events;
    }

    @Override
    public List<Comment> listComments(Cname cname) {
        return threads.findOrCreate(cname).comments();
    }

    @Override
    public Comment postComment(PostCommentCommand command) {
        RoomCommentThread thread = threads.findOrCreate(command.cname());
        Optional<Comment.ReplyTarget> replyTo = command.replyToMessageId()
            .map(id -> new Comment.ReplyTarget(id, command.author(), ""));
        Comment comment = thread.post(command.author(), command.text(), replyTo);
        threads.save(thread);
        events.publish(new RoomEvent.CommentPosted(command.cname(), comment));
        return comment;
    }
}
