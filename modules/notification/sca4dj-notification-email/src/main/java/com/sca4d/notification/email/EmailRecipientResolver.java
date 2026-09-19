package com.sca4d.notification.email;

import com.sca4d.core.api.notification.NotificationRecipient;
import org.jetbrains.annotations.CheckReturnValue;
import org.jetbrains.annotations.NotNull;

import java.util.Optional;

/** 把通知内部接收人解析为当前有效邮箱，具体数据归属仍由 App 决定。 */
public interface EmailRecipientResolver {

    /**
     * @param recipient 不包含渠道地址的通知接收人
     * @return 当前有效邮箱；接收人不存在、停用或没有邮箱时为空
     */
    @CheckReturnValue
    @NotNull
    Optional<String> resolve(@NotNull NotificationRecipient recipient);
}
