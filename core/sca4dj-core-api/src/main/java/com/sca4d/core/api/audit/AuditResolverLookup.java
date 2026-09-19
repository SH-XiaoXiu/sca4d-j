package com.sca4d.core.api.audit;

import org.jetbrains.annotations.NotNull;

/** 按审计声明的具体类型取得目标解析器和 payload 工厂。 */
public interface AuditResolverLookup {

    /**
     * 取得审计目标解析器。
     *
     * @param resolverType `@Audit` 声明的目标解析器类型
     * @return 对应的目标解析器实例
     */
    @NotNull
    AuditTargetResolver targetResolver(
            @NotNull Class<? extends AuditTargetResolver> resolverType);

    /**
     * 取得审计 payload 工厂。
     *
     * @param factoryType `@Audit` 声明的 payload 工厂类型
     * @return 对应的 payload 工厂实例
     */
    @NotNull
    AuditPayloadFactory payloadFactory(
            @NotNull Class<? extends AuditPayloadFactory> factoryType);
}
