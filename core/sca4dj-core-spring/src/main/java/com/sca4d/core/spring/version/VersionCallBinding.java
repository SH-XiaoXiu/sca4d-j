package com.sca4d.core.spring.version;

import com.sca4d.core.spring.call.CallLifecycleAdvisor;
import org.jetbrains.annotations.NotNull;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestMapping;

import java.lang.reflect.Method;

/** 只将受版本规则保护的 HTTP 方法接入 Call，不处理异常响应。 */
public final class VersionCallBinding implements CallLifecycleAdvisor.MethodBinding {

    private static final String SPRINGDOC_CONTROLLER_PACKAGE = "org.springdoc.";

    /** @return 方法是否为需要版本检查的 Controller 入口 */
    @Override
    public boolean matches(@NotNull Method method, @NotNull Class<?> targetType) {
        return AnnotatedElementUtils.hasAnnotation(targetType, Controller.class)
                && !targetType.getName().startsWith(SPRINGDOC_CONTROLLER_PACKAGE)
                && AnnotatedElementUtils.hasAnnotation(method, RequestMapping.class)
                && !AnnotatedElementUtils.hasAnnotation(method, VersionDiscovery.class)
                && !AnnotatedElementUtils.hasAnnotation(targetType, VersionDiscovery.class);
    }
}
