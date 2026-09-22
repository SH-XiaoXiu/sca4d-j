package com.sca4d.core.spring.call;

import com.sca4d.core.api.audit.AuditPayloadFactory;
import com.sca4d.core.api.audit.AuditResolverLookup;
import com.sca4d.core.api.audit.AuditTargetResolver;
import org.jetbrains.annotations.NotNull;
import org.springframework.context.ApplicationContext;

import java.util.Objects;

/** 从 Spring 容器取得 `@Audit` 声明的目标解析器和 payload 工厂。 */
public final class SpringAuditResolverLookup implements AuditResolverLookup {

    private final ApplicationContext applicationContext;

    /**
     * 创建 Spring 审计解析器查找入口。
     *
     * @param applicationContext 当前 Spring 容器
     */
    public SpringAuditResolverLookup(ApplicationContext applicationContext) {
        this.applicationContext = Objects.requireNonNull(applicationContext, "Spring 容器不能为空");
    }

    /** @param resolverType 审计目标解析器类型 @return Spring 容器中的解析器 */
    public @NotNull AuditTargetResolver targetResolver(
            @NotNull Class<? extends AuditTargetResolver> resolverType) {
        return applicationContext.getBean(resolverType);
    }

    /** @param factoryType 审计载荷工厂类型 @return Spring 容器中的载荷工厂 */
    public @NotNull AuditPayloadFactory payloadFactory(
            @NotNull Class<? extends AuditPayloadFactory> factoryType) {
        return applicationContext.getBean(factoryType);
    }
}
