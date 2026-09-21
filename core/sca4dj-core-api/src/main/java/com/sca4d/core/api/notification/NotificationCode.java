package com.sca4d.core.api.notification;

import com.sca4d.core.api.code.TreeCode;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Objects;

/** 表示跨通知调用方、模板和投递实现保持稳定的通知编码。 */
public final class NotificationCode {

    private final @NotNull TreeCode value;

    /**
     * 创建通知编码。
     *
     * @param value 具有领域层级的稳定编码
     */
    public NotificationCode(@NotNull String value) {
        this(TreeCode.of(value));
    }

    /** 创建已经规范化的通知编码。 */
    public NotificationCode(@NotNull TreeCode value) {
        this.value = Objects.requireNonNull(value, "通知编码不能为空");
    }

    /** @return 稳定通知编码 */
    @Contract(pure = true)
    public @NotNull String value() {
        return value.value();
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
        return value.value();
    }
}
