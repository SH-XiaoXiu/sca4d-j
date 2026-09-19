package com.sca4d.core.api.notification;

import com.sca4d.core.api.region.Region;
import org.jetbrains.annotations.NotNull;

import java.util.Locale;

/** 向通知类型回调提供接收人、语言和区域等领域化发布操作。 */
public interface NotificationExecution {

    /** 指定本次通知使用的 Locale；未指定时由框架按接收人和区域解析。 */
    @NotNull
    NotificationExecution locale(@NotNull Locale locale);

    /** 指定本次通知发生或面向的业务区域。 */
    @NotNull
    NotificationExecution region(@NotNull Region region);

    /** 把通知发送给指定 Account。 */
    void sendToAccount(long accountId);

    /** 把通知发送给开放的内部接收人类型。 */
    void sendTo(@NotNull NotificationRecipient recipient);
}
