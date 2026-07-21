package com.jilali.roomcontext.application.port.out;

import com.jilali.comment.dto.CaptionHistoryResponse;
import com.jilali.comment.dto.CaptionSwitchRequest;
import com.jilali.comment.dto.CommentListDto;
import com.jilali.comment.dto.SendCommentRequest;

public interface CommentUpstreamPort {
    CaptionHistoryResponse captionHistory(int busiType, String cname, int pageSize);
    void captionSwitch(CaptionSwitchRequest request);
    CommentListDto comments(int busiType, String cname);
    Object sendComment(SendCommentRequest request);
}
