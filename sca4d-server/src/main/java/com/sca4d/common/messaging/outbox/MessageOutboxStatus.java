package com.sca4d.common.messaging.outbox;

import com.sca4d.common.enums.BaseEnum;
import org.jetbrains.annotations.NotNull;

/**
 * 消息 Outbox 状态。
 */
public enum MessageOutboxStatus implements BaseEnum {

    /** 待发送。 */
    PENDING("待发送"),
    /** 发送中。 */
    SENDING("发送中"),
    /** 已发送到 MQ broker。 */
    SENT("已发送"),
    /** 发送失败，等待重试。 */
    FAILED("发送失败"),
    /** 超过重试次数，进入发布侧死信。 */
    DEAD("发布死信");

    private final String value;
    private final String description;

    MessageOutboxStatus(@NotNull String description) {
        this.value = BaseEnum.normalize(name());
        this.description = description;
    }

    @Override
    public @NotNull String value() {
        return value;
    }

    @Override
    public @NotNull String description() {
        return description;
    }
}
