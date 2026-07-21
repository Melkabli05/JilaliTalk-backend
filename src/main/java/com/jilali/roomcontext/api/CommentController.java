package com.jilali.roomcontext.api;

import com.jilali.roomcontext.infrastructure.dto.comment.BffSendCommentRequest;
import com.jilali.roomcontext.infrastructure.dto.comment.CaptionHistoryResponse;
import com.jilali.roomcontext.infrastructure.dto.comment.CaptionSwitchRequest;
import com.jilali.roomcontext.infrastructure.dto.comment.CommentListDto;
import com.jilali.roomcontext.infrastructure.dto.comment.SendCommentRequest;
import com.jilali.roomcontext.infrastructure.dto.comment.SendCommentResponse;
import com.jilali.roomcontext.application.port.out.CommentUpstreamPort;
import io.micronaut.core.annotation.Nullable;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.annotation.Body;
import io.micronaut.http.annotation.Controller;
import io.micronaut.http.annotation.Get;
import io.micronaut.http.annotation.Post;
import io.micronaut.http.annotation.QueryValue;
import io.micronaut.scheduling.TaskExecutors;
import io.micronaut.scheduling.annotation.ExecuteOn;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Map;

/** New-architecture controller, temporarily mounted under {@code /api/v2} - see
 *  TranslateController's Javadoc for the coexistence rationale. */
@ExecuteOn(TaskExecutors.BLOCKING)
@Controller("/api/v2")
public class CommentController {

    private static final DateTimeFormatter SEND_TIME_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final CommentUpstreamPort upstream;

    public CommentController(CommentUpstreamPort upstream) {
        this.upstream = upstream;
    }

    @Get("/captions/history")
    public CaptionHistoryResponse history(@QueryValue(defaultValue = "2") int busiType,
                                           @QueryValue @NotBlank String cname,
                                           @QueryValue(defaultValue = "20") int pageSize) {
        return upstream.captionHistory(busiType, cname, pageSize);
    }

    @Post("/captions/switch")
    public HttpResponse<Void> switchCaption(@Valid @Body CaptionSwitchRequest request) {
        upstream.captionSwitch(request);
        return HttpResponse.noContent();
    }

    @Get("/comments")
    public CommentListDto comments(@QueryValue(defaultValue = "2") int busiType,
                                    @QueryValue @NotBlank String cname) {
        return upstream.comments(busiType, cname);
    }

    @Post("/comments")
    public HttpResponse<SendCommentResponse> sendComment(@Valid @Body BffSendCommentRequest req) {
        SendCommentRequest.Msg.ReplyInfo replyInfo = null;
        if (req.replyInfo() != null) {
            var r = req.replyInfo();
            replyInfo = new SendCommentRequest.Msg.ReplyInfo(
                r.msgId(), r.fromId(), r.fromNickname(), r.text(),
                r.msgType() != null ? r.msgType() : "text");
        }

        Instant sentAt = Instant.now();
        var msg = new SendCommentRequest.Msg(
            "text", "normal",
            LocalDateTime.ofInstant(sentAt, ZoneId.systemDefault()).format(SEND_TIME_FMT),
            req.nickname(), "", new SendCommentRequest.Msg.Text(req.text()), replyInfo);

        var upstreamRequest = new SendCommentRequest(
            req.cname(), req.busiType(), req.nickname(), req.headUrl(), req.nationality(), req.role(), msg);

        Object data = upstream.sendComment(upstreamRequest);
        return HttpResponse.ok(new SendCommentResponse(sentAt.toEpochMilli(), extractId(data)));
    }

    @Nullable
    private String extractId(@Nullable Object data) {
        if (!(data instanceof Map<?, ?> map)) return null;
        Object id = map.containsKey("_id") ? map.get("_id") : map.get("id");
        return id == null ? null : String.valueOf(id);
    }
}
