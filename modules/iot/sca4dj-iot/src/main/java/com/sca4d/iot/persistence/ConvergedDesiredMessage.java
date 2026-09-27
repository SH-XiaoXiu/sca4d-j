package com.sca4d.iot.persistence;

import org.jetbrains.annotations.NotNull;

import java.util.Objects;

/** 保存一次批量 desired 收敛 SQL 返回的属性编码和待确认下行消息标识。 */
public class ConvergedDesiredMessage {

    private final @NotNull String propertyCode;

    private final long messageId;

    /**
     * @param propertyCode 已与 reported 值相等的属性编码
     * @param messageId 该属性当前仍未结束的最新 desired 消息标识
     */
    public ConvergedDesiredMessage(@NotNull String propertyCode, long messageId) {
        this.propertyCode = Objects.requireNonNull(propertyCode, "收敛属性编码不能为空");
        if (messageId <= 0) {
            throw new IllegalArgumentException("收敛消息标识必须为正数");
        }
        this.messageId = messageId;
    }

    /** @return 已与 reported 值相等的属性编码 */
    public @NotNull String propertyCode() {
        return propertyCode;
    }

    /** @return 该属性当前仍未结束的最新 desired 消息标识 */
    public long messageId() {
        return messageId;
    }
}
