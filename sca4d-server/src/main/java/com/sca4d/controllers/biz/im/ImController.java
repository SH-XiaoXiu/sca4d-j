package com.sca4d.controllers.biz.im;

import com.sca4d.common.auth.Principal;
import com.sca4d.common.auth.Subject;
import com.sca4d.common.auth.annotation.AuthPrincipal;
import com.sca4d.common.auth.annotation.AuthSubject;
import com.sca4d.common.permission.annotation.RequirePermission;
import com.sca4d.common.web.ApiResponse;
import com.sca4d.common.web.PageData;
import com.sca4d.common.web.PageQuery;
import com.sca4d.im.ImConversation;
import com.sca4d.im.ImMessage;
import com.sca4d.im.ImPermissions;
import com.sca4d.im.ImService;
import com.sca4d.im.request.ImConversationCreateRequest;
import com.sca4d.im.request.ImMessageCreateRequest;
import com.sca4d.im.view.ImConversationView;
import com.sca4d.im.view.ImMessageView;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.jetbrains.annotations.NotNull;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * IM HTTP 接口。
 */
@RestController
@RequestMapping("/im")
@AuthPrincipal
@RequiredArgsConstructor
public class ImController {

    private final ImService service;

    /**
     * 分页查询当前主体会话。
     *
     * @param request 分页请求
     * @return 会话分页
     */
    @GetMapping("/conversations")
    @RequirePermission(ImPermissions.CONVERSATIONS_READ)
    public @NotNull ApiResponse<PageData<ImConversationView>> conversations(
            @NotNull PageQuery request, @AuthPrincipal @NotNull Principal principal) {
        return ApiResponse.ok(service.conversations(request.page(), request.size(), principal)
                .map(ImConversationView::from));
    }

    /**
     * 创建 IM 会话。
     *
     * @param request 创建请求
     * @return 会话创建结果
     */
    @PostMapping("/conversations")
    @RequirePermission(ImPermissions.CONVERSATIONS_CREATE)
    public @NotNull ApiResponse<ImConversationView> createConversation(
            @Valid @RequestBody @NotNull ImConversationCreateRequest request,
            @AuthSubject @NotNull Subject subject,
            @AuthPrincipal @NotNull Principal principal) {
        ImConversation conversation = service.createConversation(
                request.getConversationType(), request.getTitle(), request.getMembers(), subject, principal);
        return ApiResponse.created(ImConversationView.from(conversation));
    }

    /**
     * 分页查询会话消息。
     *
     * @param conversationId 会话主键
     * @param request 分页请求
     * @return 消息分页
     */
    @GetMapping("/conversations/{conversationId}/messages")
    @RequirePermission(ImPermissions.MESSAGES_READ)
    public @NotNull ApiResponse<PageData<ImMessageView>> messages(
            @PathVariable @NotNull Long conversationId,
            @NotNull PageQuery request,
            @AuthPrincipal @NotNull Principal principal) {
        return ApiResponse.ok(service.messages(conversationId, request.page(), request.size(), principal)
                .map(ImMessageView::from));
    }

    /**
     * 发送 IM 消息。
     *
     * @param request 发送请求
     * @return 已创建消息
     */
    @PostMapping("/conversations/{conversationId}/messages")
    @RequirePermission(ImPermissions.MESSAGES_CREATE)
    public @NotNull ApiResponse<ImMessageView> createMessage(
            @PathVariable @NotNull Long conversationId,
            @Valid @RequestBody @NotNull ImMessageCreateRequest request,
            @AuthSubject @NotNull Subject subject,
            @AuthPrincipal @NotNull Principal principal) {
        ImMessage message = service.send(
                conversationId,
                request.getContentType(),
                request.getContent(),
                request.getAttachmentFileId(),
                request.getAttachmentUploadGrant(),
                request.getPayload(),
                subject,
                principal);
        return ApiResponse.created(ImMessageView.from(message));
    }

    /**
     * 标记消息已读。
     *
     * @param messageId 消息主键
     * @return 成功响应
     */
    @PutMapping("/messages/{messageId}/read-status")
    @RequirePermission(ImPermissions.MESSAGES_UPDATE)
    public @NotNull ApiResponse<Void> updateReadStatus(
            @PathVariable @NotNull Long messageId,
            @AuthPrincipal @NotNull Principal principal) {
        service.read(messageId, principal);
        return ApiResponse.ok();
    }

    /**
     * 撤回自己发送的消息。
     *
     * @param messageId 消息主键
     * @return 成功响应
     */
    @DeleteMapping("/messages/{messageId}")
    @RequirePermission(ImPermissions.MESSAGES_UPDATE)
    public @NotNull ApiResponse<Void> deleteMessage(
            @PathVariable @NotNull Long messageId, @AuthPrincipal @NotNull Principal principal) {
        service.revoke(messageId, principal);
        return ApiResponse.ok();
    }
}
