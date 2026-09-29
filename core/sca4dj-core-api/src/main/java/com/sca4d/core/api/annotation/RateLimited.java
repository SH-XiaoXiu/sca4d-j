package com.sca4d.core.api.annotation;

import com.sca4d.core.api.call.CallLifecycle;
import com.sca4d.core.api.call.CallValueResolver;
import com.sca4d.core.api.concurrent.NoRateLimitKeyResolver;
import com.sca4d.core.api.concurrent.RateLimit;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/** 声明调用必须先取得一个或多个业务维度的限流额度。 */
@Documented
@Target({ElementType.TYPE, ElementType.METHOD})
@Retention(RetentionPolicy.RUNTIME)
@CallLifecycle.Intercepted
public @interface RateLimited {

    /**
     * 返回需要分别取得额度的内置限流维度。
     *
     * <p>默认按当前方法的全部调用共享额度。声明多个维度时，只有全部维度仍有额度才会执行当前调用。
     *
     * @return 内置限流维度
     */
    RateLimit.Dim[] dimensions() default {RateLimit.Dim.GLOBAL};

    /**
     * 返回额外自定义限流维度使用的业务键解析器。
     *
     * <p>指定解析器时，其非空白字符串结果与 {@link #dimensions()} 一起原子取得额度。把 {@link #dimensions()}
     * 声明为空数组时，可以只使用自定义维度。
     *
     * @return 自定义业务键解析器；默认不增加自定义维度
     */
    Class<? extends CallValueResolver<?>> keyResolver() default NoRateLimitKeyResolver.class;

    /** 从方法参数读取自定义限流键的表达式；与 `keyResolver` 二选一。 */
    String key() default "";

    /**
     * 返回时间窗口内允许的调用次数。
     *
     * @return 时间窗口内允许的调用次数
     */
    long permits();

    /**
     * 返回时间窗口秒数。
     *
     * @return 时间窗口秒数
     */
    long windowSeconds();
}
