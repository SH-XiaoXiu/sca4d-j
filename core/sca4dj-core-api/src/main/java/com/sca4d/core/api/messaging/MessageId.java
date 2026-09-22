package com.sca4d.core.api.messaging;

import com.sca4d.core.api.id.Id;

import java.util.Objects;

/** 标识一次逻辑消息发布，并供 Consumer 对重复投递执行幂等检查。 */
public final class MessageId extends Id {

    /**
     * 创建消息标识。
     *
     * @param value 在 `IdGenerator` 约束内唯一的正数标识
     */
    public MessageId(long value) {
        super(value);
    }

    /** 使用已经校验的正数标识创建消息 ID。 */
    public MessageId(Id value) {
        super(Objects.requireNonNull(value, "消息标识不能为空").longValue());
    }
}
