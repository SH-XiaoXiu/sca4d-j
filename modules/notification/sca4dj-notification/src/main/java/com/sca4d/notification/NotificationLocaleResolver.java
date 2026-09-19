package com.sca4d.notification;

import com.sca4d.core.api.notification.NotificationRecipient;
import org.jetbrains.annotations.NotNull;

import java.util.Locale;

/** 按内部接收人读取后端通知实际使用的全局语言。 */
@FunctionalInterface
public interface NotificationLocaleResolver {

    /** @return 接收人保存的语言，未保存时返回应用默认 Locale */
    @NotNull
    Locale resolve(@NotNull NotificationRecipient recipient);
}
