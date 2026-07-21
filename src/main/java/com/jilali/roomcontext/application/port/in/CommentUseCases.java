package com.jilali.roomcontext.application.port.in;

import com.jilali.roomcontext.application.command.comment.CommentCommands.PostCommentCommand;
import com.jilali.roomcontext.domain.model.Comment;
import com.jilali.roomcontext.domain.valueobject.Cname;

import java.util.List;

public interface CommentUseCases {
    List<Comment> listComments(Cname cname);
    Comment postComment(PostCommentCommand command);
}
