package com.sca4d.core.api.messaging;

import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/** 标识一次逻辑消息发布，并供 Consumer 对重复投递执行幂等检查。 */
public final class MessageId {

    private final long value;

    /**
     * 创建消息标识。
     *
     * @param value 在 `IdGenerator` 约束内唯一的正数标识
     */
    public MessageId(long value) {
        if (value <= 0) {
            throw new IllegalArgumentException("消息标识必须为正数");
        }
        this.value = value;
    }

    /** @return 在 `IdGenerator` 约束内唯一的正数标识 */
    @Contract(pure = true)
    public long value() {
        return value;
    }

    /** @return 消息标识的十进制文本 */
    @Override
    @Contract(pure = true)
    public @NotNull String toString() {
        return Long.toString(value);
    }

    /** @return 两个 MessageId 是否包含相同标识 */
    @Override
    @Contract(value = "null -> false", pure = true)
    public boolean equals(@Nullable Object other) {
        return this == other || other instanceof MessageId that && value == that.value;
    }

    /** @return 消息标识哈希值 */
    @Override
    @Contract(pure = true)
    public int hashCode() {
        return Long.hashCode(value);
    }
}
