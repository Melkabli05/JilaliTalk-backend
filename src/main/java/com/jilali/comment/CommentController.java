package com.jilali.comment;

import com.jilali.client.JilaliClient;
import com.jilali.client.JilaliResponses;
import com.jilali.comment.dto.BffSendCommentRequest;
import com.jilali.comment.dto.CaptionHistoryResponse;
import com.jilali.comment.dto.CaptionSwitchRequest;
import com.jilali.comment.dto.Comment;
import com.jilali.comment.dto.CommentDto;
import com.jilali.comment.dto.CommentListDto;
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
    public CommentListDto comments(@QueryValue(defaultValue = "2") int busiType,
                                        @QueryValue @NotBlank String cname) {
        var upstream = JilaliResponses.unwrap(client.comments(busiType, cname));
        return new CommentListDto(
                upstream.items().stream().map(this::toDto).toList(),
                upstream.hasNext(),
                upstream.oldestId()
        );
    }

    /** Converts an upstream Comment (createdAt/updatedAt in Unix seconds) to CommentDto (milliseconds). */
    private CommentDto toDto(Comment c) {
        return new CommentDto(
                c.id(), c.createdAt() * 1000L, c.updatedAt() * 1000L,
                c.cname(), c.busiType(), c.userId(), c.nickname(), c.headUrl(),
                c.nationality(), c.role(), c.vipType(), toMsgDto(c.msg()), c.dayRankLevel(),
                c.giftLevel(), c.fgLevel(), c.fgName(), c.fgIsActive(), c.bubbleId(),
                c.bubbleUrl(), c.bubbleColor(), c.hitBad(), c.bubbleAnimalType(),
                c.bubbleAnimalUrl(), c.vipLogo(), c.vipLogoAnim(), c.expireAt(), c.medalWallIcon()
        );
    }

    private CommentDto.Msg toMsgDto(Comment.Msg msg) {
        if (msg == null) return null;
        return new CommentDto.Msg(msg.text() == null ? null : new CommentDto.Msg.Text(msg.text().text()));
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
}
