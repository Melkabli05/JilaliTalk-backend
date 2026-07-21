package com.jilali.roomcontext.application.port.out;

import com.jilali.roomcontext.infrastructure.dto.comment.CaptionHistoryResponse;
import com.jilali.roomcontext.infrastructure.dto.comment.CaptionSwitchRequest;
import com.jilali.roomcontext.infrastructure.dto.comment.CommentListDto;
import com.jilali.roomcontext.infrastructure.dto.comment.SendCommentRequest;

public interface CommentUpstreamPort {
    CaptionHistoryResponse captionHistory(int busiType, String cname, int pageSize);
    void captionSwitch(CaptionSwitchRequest request);
    CommentListDto comments(int busiType, String cname);
    Object sendComment(SendCommentRequest request);
}
