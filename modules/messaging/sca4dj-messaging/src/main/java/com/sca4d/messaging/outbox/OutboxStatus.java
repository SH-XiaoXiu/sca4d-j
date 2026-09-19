package com.sca4d.messaging.outbox;

import com.sca4d.core.api.enums.BaseEnum;

/** 表示 Outbox 记录等待、发送中、失败待重试或已经发送。 */
public enum OutboxStatus implements BaseEnum {
    /** 等待达到最早发布时间。 */
    PENDING,
    /** 已被一个实例租用并正在发布。 */
    SENDING,
    /** 上次发布失败，等待下一次重试。 */
    FAILED,
    /** RabbitMQ 已确认消息发布。 */
    SENT;

    /** @return 数据库保存的状态值 */
    @Override
    public String value() {
        return name();
    }

    /** @return 状态含义 */
    @Override
    public String description() {
        return switch (this) {
            case PENDING -> "等待发布";
            case SENDING -> "正在发布";
            case FAILED -> "等待重试";
            case SENT -> "发布完成";
        };
    }
}
