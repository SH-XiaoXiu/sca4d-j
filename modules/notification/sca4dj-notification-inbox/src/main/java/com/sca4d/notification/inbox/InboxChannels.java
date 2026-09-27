package com.sca4d.notification.inbox;

import com.sca4d.core.api.name.code.TreeCode;
import org.jetbrains.annotations.NotNull;

/**
 * Inbox 模块声明的通知渠道。
 */
public final class InboxChannels {

    public static final @NotNull TreeCode INBOX = TreeCode.of("NOTIFICATION/CHANNEL/INBOX");

    private InboxChannels() {
    }
}
