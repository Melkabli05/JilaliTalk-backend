package com.jilali.comment;

import com.jilali.client.JilaliClient;
import com.jilali.client.JilaliResponses;
import com.jilali.comment.dto.BffSendCommentRequest;
import com.jilali.comment.dto.CaptionHistoryResponse;
import com.jilali.comment.dto.CaptionSwitchRequest;
import com.jilali.comment.dto.CommentListResponse;
import com.jilali.comment.dto.CommentNotifyResponse;
import com.jilali.comment.dto.SendCommentRequest;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.annotation.Body;
import io.micronaut.http.annotation.Controller;
import io.micronaut.http.annotation.Get;
import io.micronaut.http.annotation.Post;
import io.micronaut.http.annotation.QueryValue;
import io.micronaut.scheduling.annotation.ExecuteOn;
import io.micronaut.scheduling.TaskExecutors;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * Comments and captions. Base path is {@code /api} with explicit sub-paths so caption routes live
 * under {@code /api/captions/*} and comment routes under {@code /api/comments/*} — nesting comments
 * beneath a "captions" prefix would be misleading.
 */
@ExecuteOn(TaskExecutors.BLOCKING)
@Controller("/api")
public class CommentController {

    private final JilaliClient client;

    public CommentController(JilaliClient client) {
        this.client = client;
    }

    // ---- Captions ----

    @Get("/captions/history")
    public CaptionHistoryResponse history(@QueryValue(defaultValue = "2") int busiType,
                                          @QueryValue @NotBlank String cname,
                                          @QueryValue(defaultValue = "20") int pageSize) {
        return JilaliResponses.unwrap(client.captionHistory(busiType, cname, pageSize));
    }

    @Post("/captions/switch")
    public HttpResponse<Void> switchCaption(@Valid @Body CaptionSwitchRequest request) {
        JilaliResponses.unwrap(client.captionSwitch(request));
        return HttpResponse.noContent();
    }

    // ---- Comments ----

    @Get("/comments")
    public CommentListResponse comments(@QueryValue(defaultValue = "2") int busiType,
                                        @QueryValue @NotBlank String cname) {
        return JilaliResponses.unwrap(client.comments(busiType, cname));
    }

    private static final DateTimeFormatter SEND_TIME_FMT =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    @Post("/comments")
    public HttpResponse<Void> sendComment(@Valid @Body BffSendCommentRequest req) {
        SendCommentRequest.Msg.ReplyInfo replyInfo = null;
        if (req.replyInfo() != null) {
            var r = req.replyInfo();
            replyInfo = new SendCommentRequest.Msg.ReplyInfo(
                    r.msgId(), r.fromId(), r.fromNickname(), r.text(),
                    r.msgType() != null ? r.msgType() : "text");
        }

        var msg = new SendCommentRequest.Msg(
                "text",
                "normal",
                LocalDateTime.now().format(SEND_TIME_FMT),
                req.nickname(),
                "",
                new SendCommentRequest.Msg.Text(req.text()),
                replyInfo);

        var upstream = new SendCommentRequest(
                req.cname(), req.busiType(), req.nickname(),
                req.headUrl(), req.nationality(), req.role(), msg);

        JilaliResponses.unwrap(client.sendComment(upstream));
        return HttpResponse.noContent();
    }

    @Get("/comments/notify")
    public CommentNotifyResponse commentNotify(@QueryValue(defaultValue = "2") int busiType) {
        return JilaliResponses.unwrap(client.commentNotify(busiType));
    }
}
