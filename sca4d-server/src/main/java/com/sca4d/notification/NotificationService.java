package com.sca4d.notification;

import com.sca4d.common.auth.Principal;
import com.sca4d.common.auth.PrincipalType;
import com.sca4d.common.auth.Subject;
import com.sca4d.common.validation.SafeText;
import com.sca4d.common.web.PageData;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Map;

/**
 * 通知服务接口。
 */
public interface NotificationService {

    /**
     * 创建通知并投递给接收人。
     *
     * @param title 最长 200 字符的通知标题
     * @param content 最长 2000 字符的通知正文
     * @param category 通知分类
     * @param senderPrincipalType 发送主体类型
     * @param senderPrincipalId 发送主体主键
     * @param payload 扩展载荷
     * @param recipients 至少包含一个合法目标的接收人列表
     * @return 通知主键
     */
    @NotNull
    Long create(
            @SafeText(max = 200) @NotNull String title,
            @SafeText(max = 2000) @NotNull String content,
            @NotNull NotificationCategory category,
            @Nullable PrincipalType senderPrincipalType,
            @Nullable Long senderPrincipalId,
            @Nullable Map<String, Object> payload,
            @NotNull List<NotificationRecipient> recipients);

    /**
     * 分页查询当前用户通知。
     *
     * @param unreadOnly 是否只返回未读通知
     * @param page 页码，从 1 开始
     * @param size 每页记录数
     * @param subject 当前登录身份
     * @param principal 当前执行主体
     * @return 当前用户通知分页
     */
    @NotNull
    PageData<NotificationMessage> page(
            boolean unreadOnly,
            long page,
            long size,
            @NotNull Subject subject,
            @NotNull Principal principal);

    /**
     * 查询当前用户未读通知数量。
     *
     * @param subject 当前登录身份
     * @param principal 当前执行主体
     * @return 未读通知数量
     */
    long unreadCount(@NotNull Subject subject, @NotNull Principal principal);

    /**
     * 标记当前用户指定通知已读。
     *
     * @param notificationId 通知主键
     * @param subject 当前登录身份
     * @param principal 当前执行主体
     */
    void read(
            @NotNull Long notificationId,
            @NotNull Subject subject,
            @NotNull Principal principal);

    /**
     * 标记当前用户所有通知已读。
     *
     * @param subject 当前登录身份
     * @param principal 当前执行主体
     */
    void readAll(@NotNull Subject subject, @NotNull Principal principal);
}
