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
import com.jilali.comment.dto.SendCommentResponse;
import io.micronaut.core.annotation.Nullable;
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
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Map;

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
        return new CommentDto.Msg(
                msg.text() == null ? null : new CommentDto.Msg.Text(msg.text().text()),
                toReplyInfoDto(msg.replyInfo()));
    }

    private CommentDto.Msg.ReplyInfo toReplyInfoDto(Comment.Msg.ReplyInfo r) {
        if (r == null) return null;
        return new CommentDto.Msg.ReplyInfo(r.msgId(), r.fromId(), r.fromNickname(), r.text(), r.msgType());
    }

    private static final DateTimeFormatter SEND_TIME_FMT =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    /**
     * POST /comments used to return bare 204 No Content, giving the frontend nothing to
     * reconcile its optimistic local insert against besides guess-matching the realtime WS
     * echo by same-user + a fuzzy time window — which was producing visible duplicate
     * comments whenever that heuristic missed. Returning the BFF's own send instant here
     * (the exact same one used to build the upstream request's send_time, so there's no
     * drift between what upstream was told and what we report back) lets the frontend
     * tighten that reconciliation. `id` is a bonus, best-effort extraction from whatever
     * upstream's response happens to contain — never required.
     */
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
                "text",
                "normal",
                LocalDateTime.ofInstant(sentAt, ZoneId.systemDefault()).format(SEND_TIME_FMT),
                req.nickname(),
                "",
                new SendCommentRequest.Msg.Text(req.text()),
                replyInfo);

        var upstream = new SendCommentRequest(
                req.cname(), req.busiType(), req.nickname(),
                req.headUrl(), req.nationality(), req.role(), msg);

        Object data = JilaliResponses.unwrap(client.sendComment(upstream));
        return HttpResponse.ok(new SendCommentResponse(sentAt.toEpochMilli(), extractId(data)));
    }

    /** Best-effort: upstream's sendComment response shape isn't documented, so this only
     *  returns something when `data` turns out to be a JSON object with a recognizable id
     *  field — anything else (null, a different shape) just means no bonus id, not an error. */
    @Nullable
    private String extractId(@Nullable Object data) {
        if (!(data instanceof Map<?, ?> map)) return null;
        Object id = map.containsKey("_id") ? map.get("_id") : map.get("id");
        return id == null ? null : String.valueOf(id);
    }
}
