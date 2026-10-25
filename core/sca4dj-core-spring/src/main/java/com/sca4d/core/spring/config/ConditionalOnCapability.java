package com.sca4d.core.spring.config;

import org.springframework.context.annotation.Conditional;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/** 按 `sca4dj.instance.capabilities` 决定被标注的类或 Bean 方法是否参与容器装配；功能声明 Bean（`InstanceCapability`）本身不应使用此注解。 */
@Documented
@Target({ElementType.TYPE, ElementType.METHOD})
@Retention(RetentionPolicy.RUNTIME)
@Conditional(InstanceCapabilityCondition.class)
public @interface ConditionalOnCapability {
    /** 功能编码，与 `sca4dj.instance.capabilities` 中的配置值按 TreeCode 比较是否相等。 */
    String value();
}
