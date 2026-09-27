package com.sca4d.core.api.annotation;

import com.sca4d.core.api.call.CallValueResolver;
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
public @interface Idempotent {

    /**
     * 返回用于解析 {@link IdempotencyKey} 的解析器。
     *
     * @return 用于解析 {@link IdempotencyKey} 的解析器
     */
    Class<? extends CallValueResolver<?>> keyResolver();

    /**
     * 返回成功结果保留秒数。
     *
     * @return 成功结果保留秒数
     */
    long retentionSeconds();
}
