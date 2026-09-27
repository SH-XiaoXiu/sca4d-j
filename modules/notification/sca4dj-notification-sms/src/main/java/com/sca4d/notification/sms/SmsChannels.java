package com.sca4d.notification.sms;

import com.sca4d.core.api.name.code.TreeCode;
import org.jetbrains.annotations.NotNull;

/**
 * SMS 模块声明的通知渠道。
 */
public class SmsChannels {

    public static final @NotNull TreeCode SMS = TreeCode.of("NOTIFICATION/CHANNEL/SMS");

    private SmsChannels() {
    }
}
