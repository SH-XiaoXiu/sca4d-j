package com.sca4d.controllers.biz.im;

import com.sca4d.common.auth.Principal;
import com.sca4d.common.auth.Subject;
import com.sca4d.common.auth.annotation.AuthPrincipal;
import com.sca4d.common.auth.annotation.AuthSubject;
import com.sca4d.common.permission.annotation.RequirePermission;
import com.sca4d.im.ImPermissions;
import com.sca4d.im.ImService;
import com.sca4d.im.request.ImMessageCreateRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.jetbrains.annotations.NotNull;
import org.springframework.messaging.handler.annotation.DestinationVariable;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Controller;

/**
 * IM STOMP 接口。
 */
@Controller
@AuthPrincipal
@RequiredArgsConstructor
public class ImStompController {

    private final ImService service;

    /**
     * 发送 IM 消息。
     *
     * @param request 发送请求
     */
    @MessageMapping("/im/conversations/{conversationId}/messages")
    @RequirePermission(ImPermissions.MESSAGES_CREATE)
    public void createMessage(
            @DestinationVariable @NotNull Long conversationId,
            @Valid @Payload @NotNull ImMessageCreateRequest request,
            @AuthSubject @NotNull Subject subject,
            @AuthPrincipal @NotNull Principal principal) {
        service.send(
                conversationId,
                request.getContentType(),
                request.getContent(),
                request.getAttachmentFileId(),
                request.getAttachmentUploadGrant(),
                request.getPayload(),
                subject,
                principal);
    }

    /**
     * 标记 IM 消息已读。
     *
     * @param messageId 消息主键
     */
    @MessageMapping("/im/messages/{messageId}/read-status")
    @RequirePermission(ImPermissions.MESSAGES_UPDATE)
    public void updateReadStatus(
            @DestinationVariable @NotNull Long messageId,
            @AuthPrincipal @NotNull Principal principal) {
        service.read(messageId, principal);
    }

}
