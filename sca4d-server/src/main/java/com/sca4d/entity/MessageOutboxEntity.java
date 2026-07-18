package com.sca4d.entity;

import com.mybatisflex.annotation.Table;
import com.sca4d.common.messaging.outbox.MessageOutboxStatus;
import lombok.Data;
import lombok.experimental.Accessors;

import java.time.OffsetDateTime;

/**
 * 消息 Outbox 实体。
 */
@Data
@Accessors(chain = true)
@Table("sys_message_outbox")
public class MessageOutboxEntity {

    private Long id;
    private String messageId;
    private String businessKey;
    private String exchangeName;
    private String routingKey;
    private String eventName;
    private String payloadJson;
    private String headersJson;
    private MessageOutboxStatus status;
    private Integer attempts;
    private Integer maxAttempts;
    private OffsetDateTime nextAttemptAt;
    private String lastError;
    private OffsetDateTime createdAt;
    private OffsetDateTime updatedAt;
    private OffsetDateTime sentAt;
    private Long version;
}
