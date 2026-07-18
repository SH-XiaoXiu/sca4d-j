package com.sca4d.controllers.biz.notification;

import com.sca4d.common.auth.Principal;
import com.sca4d.common.auth.Subject;
import com.sca4d.common.auth.annotation.AuthPrincipal;
import com.sca4d.common.auth.annotation.AuthSubject;
import com.sca4d.common.permission.annotation.RequirePermission;
import com.sca4d.common.web.ApiResponse;
import com.sca4d.common.web.PageData;
import com.sca4d.notification.NotificationPermissions;
import com.sca4d.notification.NotificationService;
import com.sca4d.notification.request.NotificationPageRequest;
import com.sca4d.notification.view.NotificationView;
import com.sca4d.notification.view.UnreadCountView;
import lombok.RequiredArgsConstructor;
import org.jetbrains.annotations.NotNull;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 当前用户通知接口。
 */
@RestController
@RequestMapping("/notifications")
@AuthPrincipal
@RequiredArgsConstructor
public class NotificationController {

    private final NotificationService service;

    /**
     * 分页查询当前用户通知。
     *
     * @param request 查询请求
     * @param subject 当前登录身份
     * @param principal 当前执行主体
     * @return 当前用户通知分页
     */
    @GetMapping
    @RequirePermission(NotificationPermissions.MESSAGES_READ)
    public @NotNull ApiResponse<PageData<NotificationView>> page(
            @NotNull NotificationPageRequest request,
            @AuthSubject @NotNull Subject subject,
            @AuthPrincipal @NotNull Principal principal) {
        return ApiResponse.ok(service
                .page(Boolean.TRUE.equals(request.getUnreadOnly()), request.page(), request.size(), subject, principal)
                .map(NotificationView::from));
    }

    /**
     * 查询当前用户未读通知数量。
     *
     * @param subject 当前登录身份
     * @param principal 当前执行主体
     * @return 未读数量视图
     */
    @GetMapping("/unread-count")
    @RequirePermission(NotificationPermissions.MESSAGES_READ)
    public @NotNull ApiResponse<UnreadCountView> unreadCount(
            @AuthSubject @NotNull Subject subject, @AuthPrincipal @NotNull Principal principal) {
        return ApiResponse.ok(new UnreadCountView(service.unreadCount(subject, principal)));
    }

    /**
     * 标记指定通知已读。
     *
     * @param notificationId 通知主键
     * @param subject 当前登录身份
     * @param principal 当前执行主体
     * @return 成功响应
     */
    @PutMapping("/{notificationId}/read-status")
    @RequirePermission(NotificationPermissions.MESSAGES_UPDATE)
    public @NotNull ApiResponse<Void> updateReadStatus(
            @PathVariable @NotNull Long notificationId,
            @AuthSubject @NotNull Subject subject,
            @AuthPrincipal @NotNull Principal principal) {
        service.read(notificationId, subject, principal);
        return ApiResponse.ok();
    }

    /**
     * 标记当前用户全部通知已读。
     *
     * @param subject 当前登录身份
     * @param principal 当前执行主体
     * @return 成功响应
     */
    @PutMapping("/read-status")
    @RequirePermission(NotificationPermissions.MESSAGES_UPDATE)
    public @NotNull ApiResponse<Void> updateAllReadStatus(
            @AuthSubject @NotNull Subject subject, @AuthPrincipal @NotNull Principal principal) {
        service.readAll(subject, principal);
        return ApiResponse.ok();
    }
}
