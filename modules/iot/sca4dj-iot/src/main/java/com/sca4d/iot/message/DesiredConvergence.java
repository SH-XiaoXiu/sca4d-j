package com.sca4d.iot.message;

import org.jetbrains.annotations.NotNull;

import java.util.Objects;

/** 表示一个 desired 属性已与 reported 相等并需要确认当前下行消息。 */
public final class DesiredConvergence {

    private final @NotNull String propertyCode;

    private final long messageId;

    /**
     * @param propertyCode 已收敛属性编码
     * @param messageId 待确认的 desired 下行消息标识
     */
    public DesiredConvergence(@NotNull String propertyCode, long messageId) {
        this.propertyCode = Objects.requireNonNull(propertyCode, "收敛属性编码不能为空");
        if (messageId <= 0) {
            throw new IllegalArgumentException("收敛消息标识必须为正数");
        }
        this.messageId = messageId;
    }

    /** @return 已收敛属性编码 */
    public @NotNull String propertyCode() {
        return propertyCode;
    }

    /** @return 待确认的 desired 下行消息标识 */
    public long messageId() {
        return messageId;
    }
}
