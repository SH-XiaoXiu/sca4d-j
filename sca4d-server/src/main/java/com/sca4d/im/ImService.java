package com.sca4d.im;

import com.sca4d.common.auth.Principal;
import com.sca4d.common.auth.Subject;
import com.sca4d.common.web.PageData;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Map;

/**
 * IM 服务接口。
 */
public interface ImService {

    /**
     * 分页查询当前主体会话。
     *
     * @param page 页码，从 1 开始
     * @param size 每页记录数
     * @param principal 当前执行主体
     * @return 当前主体会话分页
     */
    @NotNull
    PageData<ImConversation> conversations(long page, long size, @NotNull Principal principal);

    /**
     * 创建 IM 会话。
     *
     * @param conversationType 会话类型；缺失时按成员数量推断
     * @param title 会话标题；空白时不保存标题
     * @param members 初始成员 Principal 引用；缺失时只包含创建人
     * @param subject 当前登录身份
     * @param principal 当前执行主体
     * @return 已创建会话
     */
    @NotNull
    ImConversation createConversation(
            @Nullable ImConversationType conversationType,
            @Nullable String title,
            @Nullable List<ImMemberReference> members,
            @NotNull Subject subject,
            @NotNull Principal principal);

    /**
     * 分页查询会话消息。
     *
     * @param conversationId 会话主键
     * @param page 页码，从 1 开始
     * @param size 每页记录数
     * @param principal 当前执行主体
     * @return 会话消息分页
     */
    @NotNull
    PageData<ImMessage> messages(
            @NotNull Long conversationId, long page, long size, @NotNull Principal principal);

    /**
     * 发送 IM 消息。
     *
     * @param conversationId 目标会话主键
     * @param contentType 消息内容类型；缺失时使用文本类型
     * @param content 文本内容；非文本消息可以缺失
     * @param attachmentFileId 附件文件主键；非附件消息可以缺失
     * @param attachmentUploadGrant 附件上传授权；非附件消息可以缺失
     * @param payload 业务扩展载荷；缺失时编码为空对象
     * @param subject 当前登录身份
     * @param principal 当前执行主体
     * @return 已创建消息
     */
    @NotNull
    ImMessage send(
            @Nullable Long conversationId,
            @Nullable ImMessageContentType contentType,
            @Nullable String content,
            @Nullable Long attachmentFileId,
            @Nullable String attachmentUploadGrant,
            @Nullable Map<String, Object> payload,
            @NotNull Subject subject,
            @NotNull Principal principal);

    /**
     * 标记 IM 消息已读。
     *
     * @param messageId 消息主键
     * @param principal 当前执行主体
     */
    void read(@Nullable Long messageId, @NotNull Principal principal);

    /**
     * 撤回自己发送的消息。
     *
     * @param messageId 消息主键
     * @param principal 当前执行主体
     */
    void revoke(@NotNull Long messageId, @NotNull Principal principal);
}
