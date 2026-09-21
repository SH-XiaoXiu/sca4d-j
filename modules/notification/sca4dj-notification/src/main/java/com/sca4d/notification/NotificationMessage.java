package com.sca4d.notification;

import com.sca4d.core.api.code.TreeCode;
import com.sca4d.core.api.message.Message;
import com.sca4d.core.api.notification.Notification;
import org.jetbrains.annotations.NotNull;

import java.util.Objects;

/** 请求已安装的通知渠道处理一项已经冻结接收人、变量、内容和渠道要求的通知。 */
public final class NotificationMessage extends Message {

    public static final @NotNull TreeCode CODE = TreeCode.of("NOTIFICATION/DELIVERY");

    public static final int PROTOCOL_VERSION = 1;

    private final @NotNull Notification notification;

    public NotificationMessage(@NotNull Notification notification) {
        super(Objects.requireNonNull(notification, "通知不能为空").contents());
        this.notification = notification;
    }

    @Override
    public @NotNull TreeCode code() {
        return CODE;
    }

    @Override
    public int protocolVersion() {
        return PROTOCOL_VERSION;
    }

    public @NotNull Notification notification() {
        return notification;
    }
}
