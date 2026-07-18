package com.sca4d.common.concurrent.ratelimit;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 限流声明。
 *
 * <p>方法级声明优先于类级声明；同一次请求可以同时按多个维度限流。
 */
@Target({ElementType.METHOD, ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
public @interface RateLimit {

    /**
     * 返回同一次调用需要依次消费的唯一限流维度。
     *
     * @return 至少包含一个且不得重复的维度集合
     */
    RateLimitType[] types() default {RateLimitType.IP};

    /**
     * 返回 CUSTOM 维度使用的 key 或 SpEL 表达式。
     *
     * @return 不使用 CUSTOM 时为空；使用 CUSTOM 时必须计算出有效文本
     */
    String key() default "";

    /**
     * 返回桶容量覆盖值。
     *
     * @return 正数覆盖全局配置，负数继承全局配置，零属于无效声明
     */
    long capacity() default -1;

    /**
     * 返回每个窗口补充令牌数的覆盖值。
     *
     * @return 正数覆盖全局配置，负数继承全局配置，零属于无效声明
     */
    long refillTokens() default -1;

    /**
     * 返回补充窗口秒数的覆盖值。
     *
     * @return 正数覆盖全局配置，负数继承全局配置，零属于无效声明
     */
    long timeWindowSeconds() default -1;

}
