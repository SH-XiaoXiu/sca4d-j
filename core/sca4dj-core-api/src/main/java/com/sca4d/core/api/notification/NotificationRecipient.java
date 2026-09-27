package com.sca4d.core.api.notification;

import com.sca4d.core.api.name.NameFormat;
import com.sca4d.core.api.name.code.TreeCode;
import com.sca4d.core.api.utils.TextUtils;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;

import java.util.Objects;

/**
 * 保存通知接收人的内部身份类型和稳定标识，不包含渠道地址。
 */
public final class NotificationRecipient {

    /**
     * Account 接收人使用的稳定类型。
     */
    public static final @NotNull TreeCode ACCOUNT_TYPE = TreeCode.ofSegment("ACCOUNT");

    private final @NotNull TreeCode type;

    private final @NotNull String id;

    /**
     * 创建内部通知接收人。
     *
     * @param type 接收人身份类型
     * @param id   对应类型中的稳定内部标识，最长 256 个字符
     */
    public NotificationRecipient(@NotNull TreeCode type, @NotNull String id) {
        this.type = Objects.requireNonNull(type, "通知接收人类型不能为空");
        if (this.type.size() != 1
                || this.type.format(NameFormat.COLON).length() > 64
                || !Character.isLetter(this.type.format(NameFormat.COLON).charAt(0))) {
            throw new IllegalArgumentException("通知接收人类型必须是以字母开头且最长 64 个字符的单节点 TreeCode");
        }
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
        return new NotificationRecipient(NotificationRecipient.ACCOUNT_TYPE, Long.toString(accountId));
    }

    /**
     * @return 接收人内部身份类型
     */
    @Contract(pure = true)
    public @NotNull TreeCode type() {
        return type;
    }

    /**
     * @return 对应类型中的内部标识
     */
    @Contract(pure = true)
    public @NotNull String id() {
        return id;
    }
}
