package com.sca4d.core.api.notification;

import com.sca4d.core.api.code.TreeCode;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Objects;

/** 标识一种可以独立选择、渲染和投递的通知渠道。 */
public final class NotificationChannel {

    private final @NotNull TreeCode value;

    /** @param value 具有领域层级的渠道编码 */
    public NotificationChannel(@NotNull String value) {
        this(TreeCode.of(value));
    }

    /** 创建已经规范化的通知渠道编码。 */
    public NotificationChannel(@NotNull TreeCode value) {
        this.value = Objects.requireNonNull(value, "通知渠道编码不能为空");
    }

    /** @return 稳定渠道编码 */
    @Contract(pure = true)
    public @NotNull String value() {
        return value.value();
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
        return value.value();
    }
}
