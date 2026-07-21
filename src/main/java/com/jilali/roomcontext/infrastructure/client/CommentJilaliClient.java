package com.jilali.roomcontext.infrastructure.client;

import com.jilali.core.JilaliEnvelope;
import com.jilali.roomcontext.infrastructure.dto.comment.CaptionHistoryResponse;
import com.jilali.roomcontext.infrastructure.dto.comment.CaptionSwitchRequest;
import com.jilali.roomcontext.infrastructure.dto.comment.CommentListResponse;
import com.jilali.roomcontext.infrastructure.dto.comment.SendCommentRequest;
import io.micronaut.http.annotation.Body;
import io.micronaut.http.annotation.Get;
import io.micronaut.http.annotation.Post;
import io.micronaut.http.annotation.QueryValue;
import io.micronaut.http.client.annotation.Client;

/** Dedicated Comment/Caption upstream client - calls HelloTalk's {@code /livehub/comment} and
 *  {@code /livehub/caption/*} endpoints directly. Zero dependency on the legacy client.
 *  JilaliClient god interface. */
@Client(id = "jlhub", path = "/livehub")
public interface CommentJilaliClient {

    @Get("/comment")
    JilaliEnvelope<CommentListResponse> comments(@QueryValue("busi_type") int busiType, @QueryValue String cname);

    @Post("/comment")
    JilaliEnvelope<Object> sendComment(@Body SendCommentRequest body);

    @Get("/caption/history")
    JilaliEnvelope<CaptionHistoryResponse> captionHistory(@QueryValue("busi_type") int busiType,
                                                           @QueryValue String cname,
                                                           @QueryValue("page_size") int pageSize);

    @Post("/caption/switch")
    JilaliEnvelope<Object> captionSwitch(@Body CaptionSwitchRequest body);
}
