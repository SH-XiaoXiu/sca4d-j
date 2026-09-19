package com.sca4d.core.api.notification;

import com.sca4d.core.api.utils.TextUtils;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;

import java.util.Objects;

/** 保存通知接收人的内部身份类型和稳定标识，不包含渠道地址。 */
public final class NotificationRecipient {

    private final @NotNull NotificationRecipientType type;

    private final @NotNull String id;

    /**
     * 创建内部通知接收人。
     *
     * @param type 接收人身份类型
     * @param id 对应类型中的稳定内部标识，最长 256 个字符
     */
    public NotificationRecipient(@NotNull NotificationRecipientType type, @NotNull String id) {
        this.type = Objects.requireNonNull(type, "通知接收人类型不能为空");
        String checkedId = TextUtils.requireText(id, "通知接收人标识");
        if (checkedId.length() > 256) {
            throw new IllegalArgumentException("通知接收人标识不能超过 256 个字符");
        }
        this.id = checkedId;
    }

    /**
     * 创建 Account 通知接收人。
     *
     * @param accountId 正数 Account 标识
     * @return Account 接收人
     */
    @Contract(pure = true)
    public static @NotNull NotificationRecipient account(long accountId) {
        if (accountId <= 0) {
            throw new IllegalArgumentException("通知 Account 标识必须为正数");
        }
        return new NotificationRecipient(NotificationRecipientType.ACCOUNT, Long.toString(accountId));
    }

    /** @return 接收人内部身份类型 */
    @Contract(pure = true)
    public @NotNull NotificationRecipientType type() {
        return type;
    }

    /** @return 对应类型中的内部标识 */
    @Contract(pure = true)
    public @NotNull String id() {
        return id;
    }
}
