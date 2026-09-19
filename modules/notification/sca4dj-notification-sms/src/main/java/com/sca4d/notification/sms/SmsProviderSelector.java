package com.sca4d.notification.sms;

import org.jetbrains.annotations.NotNull;

import java.util.List;

/** 按具体应用已经确定的 SMS 路由排列可尝试的 Provider。 */
@FunctionalInterface
public interface SmsProviderSelector {
    /** @return 按尝试顺序排列的 Provider；不得包含停用或不支持线路的项 */
    @NotNull
    List<@NotNull SmsProvider> candidates(@NotNull SmsRoute route);
}
