package com.sca4d.notification.sms;

import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.Objects;

/** 使用数据库优先级提供 SMS Provider 默认候选顺序。 */
public class PrioritySmsProviderSelector implements SmsProviderSelector {
    private final SmsRepository repository;

    /** @param repository SMS Provider 配置持久化入口 */
    public PrioritySmsProviderSelector(@NotNull SmsRepository repository) {
        this.repository = Objects.requireNonNull(repository, "SmsRepository 不能为空");
    }

    @Override
    public @NotNull List<@NotNull SmsProvider> candidates(@NotNull SmsRoute route) {
        return repository.enabledProviders(Objects.requireNonNull(route, "SMS 路由不能为空"));
    }
}
