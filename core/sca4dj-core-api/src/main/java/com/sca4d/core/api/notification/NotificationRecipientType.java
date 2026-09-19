package com.sca4d.core.api.notification;

import com.sca4d.core.api.utils.TextUtils;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Objects;
import java.util.regex.Pattern;

/** 表示通知接收人的开放内部身份类型。 */
public final class NotificationRecipientType {

    private static final Pattern VALUE = Pattern.compile("[A-Z][A-Z0-9_-]{0,63}");

    /** Account 接收人类型。 */
    public static final @NotNull NotificationRecipientType ACCOUNT = new NotificationRecipientType("ACCOUNT");

    private final @NotNull String value;

    /**
     * 创建通知接收人类型。
     *
     * @param value 跨部署保持稳定的大写类型编码
     */
    public NotificationRecipientType(@NotNull String value) {
        String checked = TextUtils.requireText(value, "通知接收人类型");
        if (!VALUE.matcher(checked).matches()) {
            throw new IllegalArgumentException("通知接收人类型必须使用大写字母、数字、下划线或连字符");
        }
        this.value = checked;
    }

    /** @return 稳定接收人类型编码 */
    @Contract(pure = true)
    public @NotNull String value() {
        return value;
    }

    @Override
    @Contract(value = "null -> false", pure = true)
    public boolean equals(@Nullable Object other) {
        return this == other || other instanceof NotificationRecipientType type && value.equals(type.value);
    }

    @Override
    @Contract(pure = true)
    public int hashCode() {
        return Objects.hash(value);
    }

    @Override
    @Contract(pure = true)
    public @NotNull String toString() {
        return value;
    }
}
