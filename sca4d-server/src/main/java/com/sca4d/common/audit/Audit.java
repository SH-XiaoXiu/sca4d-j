package com.sca4d.common.audit;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 审计声明。
 *
 * <p>用于标记需要记录操作日志的写操作。方法级声明优先于类级声明。
 */
@Target({ElementType.TYPE, ElementType.METHOD})
@Retention(RetentionPolicy.RUNTIME)
public @interface Audit {

    /**
     * 返回固定的业务或公共组件模块编码。
     *
     * @return 用于操作日志筛选和权限展示的模块编码
     */
    String module();

    /**
     * 返回模块内固定的操作动作编码。
     *
     * @return 与具体 HTTP 路由解耦的动作编码
     */
    String action();

    /**
     * 返回当前审计声明的执行模式。
     *
     * @return 默认继承全局配置，也可按接口覆盖为异步或同步事务模式
     */
    AuditMode mode() default AuditMode.DEFAULT;

    /**
     * 审计载荷类型。
     *
     * @return 载荷类型；为空时由 payload codec 推断
     */
    String payloadType() default "";

    /**
     * 返回业务方法完成后求值的审计 Payload 表达式。
     *
     * @return 支持参数、#args、#methodName、#target、#result 和 #error 的 SpEL
     */
    String payload() default "";

    /**
     * 返回业务方法完成后求值的审计摘要表达式。
     *
     * @return 结果为 MessageReference、固定消息键文本或 null 的 SpEL
     */
    String summary() default "";

    /**
     * 目标类型。
     *
     * @return 目标类型；为空时不记录审计目标
     */
    String targetType() default "";

    /**
     * 目标 ID 表达式。
     *
     * @return 支持参数、#args、#methodName、#target、#result 和 #error 的 SpEL
     */
    String targetId() default "";

}
