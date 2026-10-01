package com.sca4d.core.api.persistence;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 声明 Repository 方法怎样解释唯一一条数据库写入的影响行数。
 *
 * <p>方法成功返回前必须执行且只能执行一条 INSERT、UPDATE 或 DELETE。影响一行表示成功，零行按照
 * {@link #whenZero()} 解释，影响多行始终表示持久化结果异常。包含多条写入或动态预期行数的方法应直接使用
 * {@link AffectedRowCounts}。Mapper 可以继续返回影响行数；使用本注解的 Repository 不读取 Mapper 返回值。
 */
@Documented
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface AffectedRows {

    /**
     * 返回发生写入的持久化对象说明，用于并发异常和行数异常。
     *
     * @return 持久化对象说明
     */
    String target();

    /**
     * 返回写入影响零行时使用的解释方式。
     *
     * @return 零行解释方式
     */
    ZeroRowPolicy whenZero() default ZeroRowPolicy.ERROR;
}
