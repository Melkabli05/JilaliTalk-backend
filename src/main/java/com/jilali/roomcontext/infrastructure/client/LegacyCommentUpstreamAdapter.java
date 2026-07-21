package com.jilali.roomcontext.infrastructure.client;

import com.jilali.client.JilaliClient;
import com.jilali.client.JilaliResponses;
import com.jilali.comment.dto.CaptionHistoryResponse;
import com.jilali.comment.dto.CaptionSwitchRequest;
import com.jilali.comment.dto.Comment;
import com.jilali.comment.dto.CommentListDto;
import com.jilali.comment.dto.SendCommentRequest;
import com.jilali.roomcontext.application.port.out.CommentUpstreamPort;
import jakarta.inject.Singleton;

/** Wraps the legacy client.JilaliClient god interface for the 4 comment/caption methods only -
 *  the JilaliClient split itself is deliberately deferred (see docs/audit/reports/
 *  dependency-analysis.md's scope/sequencing note); this adapter isolates the application layer
 *  from that god interface today, and the day JilaliClient is finally split, only this one file
 *  changes. */
@Singleton
public class LegacyCommentUpstreamAdapter implements CommentUpstreamPort {

    private final JilaliClient client;

    public LegacyCommentUpstreamAdapter(JilaliClient client) {
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
