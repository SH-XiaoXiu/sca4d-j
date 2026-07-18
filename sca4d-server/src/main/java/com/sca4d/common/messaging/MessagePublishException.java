package com.sca4d.common.messaging;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * 消息发布异常。
 */
public class MessagePublishException extends RuntimeException {

    public MessagePublishException(@NotNull String message) {
        this(message, null);
    }

    public MessagePublishException(@NotNull String message, @Nullable Throwable cause) {
        super(message, cause);
    }
}
