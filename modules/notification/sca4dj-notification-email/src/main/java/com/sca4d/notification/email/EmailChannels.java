package com.sca4d.notification.email;

import com.sca4d.core.api.notification.NotificationChannel;
import org.jetbrains.annotations.NotNull;

/** Email 模块声明的通知渠道。 */
public final class EmailChannels {

    public static final @NotNull NotificationChannel EMAIL = new NotificationChannel("NOTIFICATION/CHANNEL/EMAIL");

    private EmailChannels() {
    }
}
