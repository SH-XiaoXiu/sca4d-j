package com.sca4d.notification;

import com.sca4d.common.auth.PrincipalType;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Objects;

/**
 * 通知接收人。
 */
public class NotificationRecipient {

    private final Long subjectId;
    private final PrincipalType principalType;
    private final Long principalId;

    /**
     * 创建已经由公开工厂方法限定寻址组合的通知接收人。
     *
     * @param subjectId 可选 Subject 寻址限定
     * @param principalType 可选 Principal 类型限定，与 principalId 同时存在
     * @param principalId 可选 Principal 主键限定，与 principalType 同时存在
     */
    private NotificationRecipient(
            @Nullable Long subjectId, @Nullable PrincipalType principalType, @Nullable Long principalId) {
        this.subjectId = subjectId;
        this.principalType = principalType;
        this.principalId = principalId;
    }

    /**
     * 创建 Subject 接收人。
     *
     * @param subjectId Subject 主键
     * @return 通知接收人
     */
    public static @NotNull NotificationRecipient subject(@NotNull Long subjectId) {
        return new NotificationRecipient(subjectId, null, null);
    }

    /**
     * 创建 Principal 接收人。
     *
     * @param principalType Principal 类型
     * @param principalId Principal 主键
     * @return 通知接收人
     */
    public static @NotNull NotificationRecipient principal(
            @NotNull PrincipalType principalType, @NotNull Long principalId) {
        return new NotificationRecipient(null, principalType, principalId);
    }

    /**
     * 创建同时带 Subject 与 Principal 的接收人。
     *
     * @param subjectId Subject 主键
     * @param principalType Principal 类型
     * @param principalId Principal 主键
     * @return 通知接收人
     */
    public static @NotNull NotificationRecipient of(
            @NotNull Long subjectId, @NotNull PrincipalType principalType, @NotNull Long principalId) {
        return new NotificationRecipient(subjectId, principalType, principalId);
    }

    /**
     * 获取接收人的 Subject 限定。
     *
     * @return Subject-only 或 Subject+Principal 目标的 Subject 标识
     */
    public @Nullable Long getSubjectId() {
        return subjectId;
    }

    /**
     * 获取接收人的 Principal 类型限定。
     *
     * @return Principal-only 或 Subject+Principal 目标的类型
     */
    public @Nullable PrincipalType getPrincipalType() {
        return principalType;
    }

    /**
     * 获取接收人的 Principal 标识限定。
     *
     * @return Principal-only 或 Subject+Principal 目标的 Principal 标识
     */
    public @Nullable Long getPrincipalId() {
        return principalId;
    }

    /**
     * 按 Subject 和 Principal 寻址限定比较两个接收人。
     *
     * @param other 待比较对象
     * @return 三个寻址字段均一致时返回 {@code true}
     */
    @Override
    public boolean equals(@Nullable Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof NotificationRecipient recipient)) {
            return false;
        }
        return Objects.equals(subjectId, recipient.subjectId)
                && principalType == recipient.principalType
                && Objects.equals(principalId, recipient.principalId);
    }

    /**
     * 计算与 {@link #equals(Object)} 一致的接收人哈希值。
     *
     * @return 三个寻址字段的组合哈希值
     */
    @Override
    public int hashCode() {
        return Objects.hash(subjectId, principalType, principalId);
    }
}
