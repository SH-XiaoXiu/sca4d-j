package com.sca4d.core.api.annotation;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/** 为没有已有 Call 的 Spring 方法声明系统自主执行身份；已有调用保持原来的身份。 */
@Documented
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface SystemExecution {
    /** 启动时已经登记的稳定系统身份编码；不指定时使用 MAIN。 */
    String value() default "MAIN";
}
