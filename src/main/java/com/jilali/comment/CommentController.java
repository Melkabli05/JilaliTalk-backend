package com.jilali.comment;

import com.jilali.client.JilaliGateway;
import com.jilali.comment.dto.CaptionHistoryResponse;
import com.jilali.comment.dto.CaptionSwitchRequest;
import com.jilali.comment.dto.CommentListResponse;
import com.jilali.comment.dto.CommentNotifyResponse;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.annotation.Body;
import io.micronaut.scheduling.annotation.ExecuteOn;
import io.micronaut.http.annotation.Controller;
import io.micronaut.scheduling.TaskExecutors;
import io.micronaut.http.annotation.Get;
import io.micronaut.http.annotation.Post;
import io.micronaut.http.annotation.QueryValue;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;

/**
 * Comments and captions. Base path is {@code /api} with explicit sub-paths so caption routes live
 * under {@code /api/captions/*} and comment routes under {@code /api/comments/*} — nesting comments
 * beneath a "captions" prefix would be misleading.
 */
@ExecuteOn(TaskExecutors.BLOCKING)
@Controller("/api")
public class CommentController {

    private final JilaliGateway liveHub;

    public CommentController(JilaliGateway liveHub) {
        this.liveHub = liveHub;
    }

    // ---- Captions ----

    @Get("/captions/history")
    public CaptionHistoryResponse history(@QueryValue(defaultValue = "2") int busiType,
                                          @QueryValue @NotBlank String cname,
                                          @QueryValue(defaultValue = "20") int pageSize) {
        return liveHub.captionHistory(busiType, cname, pageSize);
    }

    @Post("/captions/switch")
    public HttpResponse<Void> switchCaption(@Valid @Body CaptionSwitchRequest request) {
        liveHub.captionSwitch(request);
        return HttpResponse.noContent();
    }

    // ---- Comments ----

    @Get("/comments")
    public CommentListResponse comments(@QueryValue(defaultValue = "2") int busiType,
                                        @QueryValue @NotBlank String cname) {
        return liveHub.comments(busiType, cname);
    }

    @Get("/comments/notify")
    public CommentNotifyResponse commentNotify(@QueryValue(defaultValue = "2") int busiType) {
        return liveHub.commentNotify(busiType);
    }
}
