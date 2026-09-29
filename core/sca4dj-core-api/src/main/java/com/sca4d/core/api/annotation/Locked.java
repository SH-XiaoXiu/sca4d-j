package com.sca4d.core.api.annotation;

import com.sca4d.core.api.call.CallLifecycle;
import com.sca4d.core.api.call.CallValueResolver;
import com.sca4d.core.api.call.NoCallValueResolver;
import com.sca4d.core.api.concurrent.LockKey;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/** 声明完整业务调用必须在一项分布式锁内连续执行。 */
@Documented
@Target({ElementType.TYPE, ElementType.METHOD})
@Retention(RetentionPolicy.RUNTIME)
@CallLifecycle.Intercepted
public @interface Locked {

    /**
     * 返回 {@link LockKey} 的解析器。
     *
     * @return 锁键解析器类型
     */
    Class<? extends CallValueResolver<?>> keyResolver() default NoCallValueResolver.class;

    /** 从方法参数读取 `LockKey` 的表达式；与 `keyResolver` 二选一。 */
    String key() default "";

    /**
     * 返回最大锁等待毫秒数。
     *
     * @return 最大锁等待毫秒数
     */
    long waitMillis();
}
