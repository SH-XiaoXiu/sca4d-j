package com.sca4d.notification.sms;

import com.sca4d.core.api.notification.NotificationRecipient;
import org.jetbrains.annotations.NotNull;

import java.util.Optional;

/** 把内部通知接收人解析成当前规范 E.164 手机号。 */
@FunctionalInterface
public interface SmsRecipientResolver {
    /** @return 可投递 E.164 手机号；接收人没有已验证手机号时为空 */
    @NotNull
    Optional<String> resolve(@NotNull NotificationRecipient recipient);
}
