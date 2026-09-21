package com.sca4d.core.api.messaging;

import com.sca4d.core.api.codec.VersionedCodec;
import com.sca4d.core.api.message.Message;

/** 把一种 Message 与 RabbitMQ/Outbox 保存的二进制 payload 互相转换。 */
public interface MessageCodec<M extends Message> extends VersionedCodec<M, byte[]> {
}
