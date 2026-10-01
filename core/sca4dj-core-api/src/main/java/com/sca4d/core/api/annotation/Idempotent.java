package com.sca4d.core.api.annotation;

import com.sca4d.core.api.call.CallLifecycle;
import com.sca4d.core.api.call.CallValueResolver;
import com.sca4d.core.api.call.NoCallValueResolver;
import com.sca4d.core.api.concurrent.IdempotencyKey;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/** 声明调用按动态幂等键复用首次成功结果。 */
@Documented
@Target({ElementType.TYPE, ElementType.METHOD})
@Retention(RetentionPolicy.RUNTIME)
@CallLifecycle.Intercepted
public @interface Idempotent {

    /**
     * 返回用于解析 {@link IdempotencyKey} 的解析器。
     *
     * @return 用于解析 {@link IdempotencyKey} 的解析器
     */
    Class<? extends CallValueResolver<?>> keyResolver() default NoCallValueResolver.class;

    /** 从方法参数或返回值读取幂等键的表达式；为空时读取当前 Call 的标准输入。 */
    String key() default "";

    /**
     * 返回成功结果保留秒数。
     *
     * @return 成功结果保留秒数；零表示使用应用默认配置
     */
    long retentionSeconds() default 0;
}
