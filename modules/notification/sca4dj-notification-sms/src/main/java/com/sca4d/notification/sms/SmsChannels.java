package com.sca4d.notification.sms;

import com.sca4d.core.api.notification.NotificationChannel;
import org.jetbrains.annotations.NotNull;

/** SMS 模块声明的通知渠道。 */
public final class SmsChannels {

    public static final @NotNull NotificationChannel SMS = new NotificationChannel("NOTIFICATION/CHANNEL/SMS");

    private SmsChannels() {
    }
}
