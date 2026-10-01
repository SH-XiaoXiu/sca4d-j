package com.sca4d.core.spring.config;

import org.springframework.context.annotation.Conditional;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/** 根据实例配置启用一个功能入口；能力声明 Bean 本身不能使用此条件。 */
@Documented
@Target({ElementType.TYPE, ElementType.METHOD})
@Retention(RetentionPolicy.RUNTIME)
@Conditional(InstanceCapabilityCondition.class)
public @interface ConditionalOnCapability {
    /** 代码常量声明的固定能力编码。 */
    String value();
}
