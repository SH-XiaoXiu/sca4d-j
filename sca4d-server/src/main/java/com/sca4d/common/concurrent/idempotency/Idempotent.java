package com.sca4d.common.concurrent.idempotency;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 幂等保护声明。
 *
 * <p>默认从方法参数中的 {@link com.sca4d.common.concurrent.IdempotentRequest} 读取幂等键；
 * key 支持 SpEL，显式声明时优先使用声明值。operation 是跨 Java 重构保持固定的业务操作编码。
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface Idempotent {

    /**
     * 返回跨路由和 Java 方法重构保持固定的业务操作编码。
     *
     * @return 用于隔离不同业务意图幂等记录的操作编码
     */
    String operation();

    /**
     * 返回幂等 key 或计算该 key 的 SpEL 表达式。
     *
     * @return 空字符串表示从 IdempotentRequest 读取客户端幂等键
     */
    String key() default "";
}
