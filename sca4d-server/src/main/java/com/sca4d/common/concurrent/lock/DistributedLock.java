package com.sca4d.common.concurrent.lock;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 分布式锁声明。
 *
 * <p>key 支持 SpEL，例如 {@code "'subject:' + #subjectId"}。方法级声明优先于类级声明。
 */
@Target({ElementType.METHOD, ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
public @interface DistributedLock {

    /**
     * 锁 key。
     *
     * @return 锁 key 或 SpEL 表达式
     */
    String key();

    /**
     * 等待获取锁的秒数；小于 0 时使用全局默认值。
     *
     * @return 等待秒数
     */
    long waitTimeSeconds() default -1;

    /**
     * 锁租约秒数；小于 0 时使用全局默认值，0 表示使用 Redisson watchdog 自动续租。
     *
     * @return 固定租约秒数或 watchdog 标记
     */
    long leaseTimeSeconds() default -1;

}
