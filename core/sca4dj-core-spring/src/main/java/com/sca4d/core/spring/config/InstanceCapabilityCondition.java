package com.sca4d.core.spring.config;

import com.sca4d.core.api.name.code.TreeCode;
import org.jetbrains.annotations.NotNull;
import org.springframework.boot.context.properties.bind.Bindable;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.context.annotation.Condition;
import org.springframework.context.annotation.ConditionContext;
import org.springframework.core.type.AnnotatedTypeMetadata;

import java.util.List;
import java.util.Objects;

/** 在 Bean 定义阶段读取实例能力选择，防止停用功能启动消费者或监听器。 */
public final class InstanceCapabilityCondition implements Condition {
    @Override
    public boolean matches(@NotNull ConditionContext context, @NotNull AnnotatedTypeMetadata metadata) {
        List<String> enabled = Binder.get(context.getEnvironment())
                .bind("sca4dj.instance.enabled-capabilities", Bindable.listOf(String.class))
                .orElse(null);
        String code = (String) Objects
                .requireNonNull(metadata.getAnnotationAttributes(ConditionalOnCapability.class.getName()))
                .get("value");
        return enabled == null || enabled.stream().anyMatch(value -> TreeCode.of(value).equals(TreeCode.of(code)));
    }
}
