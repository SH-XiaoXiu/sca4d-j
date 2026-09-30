package com.sca4d.notification;

import com.sca4d.core.api.message.Message;
import com.sca4d.core.api.name.code.TreeCode;
import com.sca4d.core.api.notification.Notification;
import org.jetbrains.annotations.NotNull;

import java.util.Objects;

/** 请求已安装的通知渠道处理一项已经冻结接收人、变量、内容和渠道要求的通知。 */
public class NotificationMessage extends Message {

    public static final int PROTOCOL_VERSION = 1;

    public static final @NotNull TreeCode CODE = TreeCode.of("NOTIFICATION/DELIVERY");

    private final @NotNull Notification notification;

    /** @param notification 已冻结接收人、变量、内容和渠道要求的通知 */
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

    /** @return 已冻结的通知内容 */
    public @NotNull Notification notification() {
        return notification;
    }
}
