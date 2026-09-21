package com.sca4d.notification.inbox;

import com.sca4d.core.api.notification.NotificationChannel;
import org.jetbrains.annotations.NotNull;

/** Inbox 模块声明的通知渠道。 */
public final class InboxChannels {

    public static final @NotNull NotificationChannel INBOX = new NotificationChannel("NOTIFICATION/CHANNEL/INBOX");

    private InboxChannels() {
    }
}
