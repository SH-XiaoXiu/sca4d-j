package com.sca4d.core.spring.call;

import com.sca4d.core.api.call.CallValueResolver;
import com.sca4d.core.api.call.CallValueResolverLookup;
import org.springframework.context.ApplicationContext;

import java.util.Objects;

/** 使用 Spring Bean 类型查找调用值解析器。 */
public class SpringCallValueResolverLookup implements CallValueResolverLookup {

    private final ApplicationContext applicationContext;

    /**
     * 创建按类型读取 Spring Bean 的解析器目录。
     *
     * @param applicationContext 当前 Spring 容器
     */
    public SpringCallValueResolverLookup(ApplicationContext applicationContext) {
        this.applicationContext = Objects.requireNonNull(applicationContext, "Spring 容器不能为空");
    }

    @Override
    public CallValueResolver<?> resolver(Class<? extends CallValueResolver<?>> resolverType) {
        return applicationContext.getBean(resolverType);
    }
}
