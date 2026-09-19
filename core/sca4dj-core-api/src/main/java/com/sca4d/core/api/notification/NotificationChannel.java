package com.sca4d.core.api.notification;

import com.sca4d.core.api.utils.TextUtils;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Objects;
import java.util.regex.Pattern;

/** 标识一种可以独立选择、渲染和投递的通知渠道。 */
public final class NotificationChannel {

    private static final Pattern VALUE = Pattern.compile("[a-z][a-z0-9-]{0,63}");

    /** 站内信渠道。 */
    public static final @NotNull NotificationChannel INBOX = new NotificationChannel("inbox");

    /** Email 渠道。 */
    public static final @NotNull NotificationChannel EMAIL = new NotificationChannel("email");

    /** SMS 渠道。 */
    public static final @NotNull NotificationChannel SMS = new NotificationChannel("sms");

    private final @NotNull String value;

    /** @param value 跨应用版本保持稳定的小写渠道编码 */
    public NotificationChannel(@NotNull String value) {
        String checked = TextUtils.requireText(value, "通知渠道编码");
        if (!VALUE.matcher(checked).matches()) {
            throw new IllegalArgumentException("通知渠道编码只能包含小写字母、数字和连字符");
        }
        this.value = checked;
    }

    /** @return 稳定渠道编码 */
    @Contract(pure = true)
    public @NotNull String value() {
        return value;
    }

    @Override
    @Contract(value = "null -> false", pure = true)
    public boolean equals(@Nullable Object other) {
        return this == other || other instanceof NotificationChannel channel && value.equals(channel.value);
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
