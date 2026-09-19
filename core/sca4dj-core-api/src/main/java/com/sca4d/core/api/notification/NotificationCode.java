package com.sca4d.core.api.notification;

import com.sca4d.core.api.utils.TextUtils;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Objects;
import java.util.regex.Pattern;

/** 表示跨通知调用方、模板和投递实现保持稳定的通知编码。 */
public final class NotificationCode {

    private static final Pattern VALUE = Pattern.compile("[A-Za-z0-9][A-Za-z0-9._-]{0,127}");

    private final @NotNull String value;

    /**
     * 创建通知编码。
     *
     * @param value 只包含字母、数字、点、下划线和连字符的稳定编码
     */
    public NotificationCode(@NotNull String value) {
        String checked = TextUtils.requireText(value, "通知编码");
        if (!VALUE.matcher(checked).matches()) {
            throw new IllegalArgumentException("通知编码只能包含字母、数字、点、下划线和连字符");
        }
        this.value = checked;
    }

    /** @return 稳定通知编码 */
    @Contract(pure = true)
    public @NotNull String value() {
        return value;
    }

    @Override
    @Contract(value = "null -> false", pure = true)
    public boolean equals(@Nullable Object other) {
        return this == other || other instanceof NotificationCode code && value.equals(code.value);
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
