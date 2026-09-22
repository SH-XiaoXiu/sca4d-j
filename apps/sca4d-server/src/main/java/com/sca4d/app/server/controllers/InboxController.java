package com.sca4d.app.server.controllers;

import com.sca4d.app.server.notification.request.MarkInboxReadRequest;
import com.sca4d.app.server.notification.view.InboxMessageView;
import com.sca4d.app.server.notification.view.InboxPageView;
import com.sca4d.app.server.platform.PlatformPrincipalSelection;
import com.sca4d.core.api.concurrent.Version;
import com.sca4d.core.spring.web.ApiResponse;
import com.sca4d.core.spring.web.annotation.CurrentAccount;
import com.sca4d.core.spring.web.annotation.CurrentPrincipal;
import com.sca4d.notification.inbox.InboxService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.jetbrains.annotations.NotNull;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Objects;

/** 查询当前 Account 站内信并修改已读状态。 */
@RestController
@RequestMapping("/me/notifications")
@Tag(name = "Current Account Notifications")
@CurrentPrincipal(selection = PlatformPrincipalSelection.class)
public final class InboxController {
    private final InboxService inbox;

    /** 创建站内信控制器。 */
    public InboxController(@NotNull InboxService inbox) {
        this.inbox = Objects.requireNonNull(inbox, "InboxService 不能为空");
    }

    /** @return 当前 Account 按标识倒序排列的站内信页和未读数量 */
    @GetMapping
    @Operation(summary = "查询当前账号站内信")
    public @NotNull ApiResponse<InboxPageView> page(
            @CurrentAccount @NotNull Long accountId,
            @RequestParam(required = false) Long beforeId,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int limit) {
        var messages = inbox.page(accountId, beforeId, limit).stream().map(InboxMessageView::from).toList();
        return ApiResponse.ok(new InboxPageView(messages, inbox.unreadCount(accountId)));
    }

    /** 按请求 Version 把当前 Account 的指定站内信标记为已读。 */
    @PostMapping("/{messageId}/read")
    public @NotNull ApiResponse<Void> markRead(
            @CurrentAccount @NotNull Long accountId,
            @PathVariable long messageId,
            @RequestBody @Valid @NotNull MarkInboxReadRequest request) {
        inbox.markRead(accountId, messageId, new Version(request.getVersion()));
        return ApiResponse.noContent();
    }

    /** 把当前 Account 尚未读取的全部站内信标记为已读。 */
    @PostMapping("/read-all")
    public @NotNull ApiResponse<Void> markAllRead(@CurrentAccount @NotNull Long accountId) {
        inbox.markAllRead(accountId);
        return ApiResponse.noContent();
    }
}
