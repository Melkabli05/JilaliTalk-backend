package com.jilali.roomcontext.infrastructure.client;

import com.jilali.roomcontext.application.port.out.CommentUpstreamPort;
import com.jilali.roomcontext.infrastructure.dto.comment.CaptionHistoryResponse;
import com.jilali.roomcontext.infrastructure.dto.comment.CaptionSwitchRequest;
import com.jilali.roomcontext.infrastructure.dto.comment.Comment;
import com.jilali.roomcontext.infrastructure.dto.comment.CommentListDto;
import com.jilali.roomcontext.infrastructure.dto.comment.SendCommentRequest;
import jakarta.inject.Singleton;

/** Dedicated Comment/Caption upstream adapter - zero dependency on the legacy client.JilaliClient
 *  god interface. */
@Singleton
public class CommentUpstreamAdapter implements CommentUpstreamPort {

    private final CommentJilaliClient client;

    public CommentUpstreamAdapter(CommentJilaliClient client) {
        this.client = client;
    }

    @Override
    public CaptionHistoryResponse captionHistory(int busiType, String cname, int pageSize) {
        return JilaliResponses.unwrap(client.captionHistory(busiType, cname, pageSize));
    }

    @Override
    public void captionSwitch(CaptionSwitchRequest request) {
        JilaliResponses.unwrap(client.captionSwitch(request));
    }

    @Override
    public CommentListDto comments(int busiType, String cname) {
        var upstream = JilaliResponses.unwrap(client.comments(busiType, cname));
        return new CommentListDto(
            upstream.items().stream().map(Comment::fromWireSeconds).toList(),
            upstream.hasNext(),
            upstream.oldestId());
    }

    @Override
    public Object sendComment(SendCommentRequest request) {
        return JilaliResponses.unwrap(client.sendComment(request));
    }
}
