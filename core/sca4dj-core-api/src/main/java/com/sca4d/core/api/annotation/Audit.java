package com.sca4d.core.api.annotation;

import com.sca4d.core.api.audit.AuditPayloadFactory;
import com.sca4d.core.api.audit.AuditTargetResolver;
import com.sca4d.core.api.audit.NoAuditPayloadFactory;
import com.sca4d.core.api.audit.NoAuditTargetResolver;
import com.sca4d.core.api.call.CallLifecycle;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/** 声明调用需要记录成功、拒绝或失败的审计结果。 */
@Documented
@Target({ElementType.TYPE, ElementType.METHOD})
@Retention(RetentionPolicy.RUNTIME)
@CallLifecycle.Intercepted
public @interface Audit {

    /** 指定审计返回前完成写入还是可靠排队；DEFAULT 只用于声明继承。 */
    enum Mode {
        /** 继承类型声明或应用配置。 */
        DEFAULT,
        /** 同步写入审计，参与已有事务；没有事务时由记录器建立事务。 */
        DIRECT,
        /** 等待独立 Outbox 事务提交，随后由消息消费者写入审计。 */
        QUEUED,
        /** 主事务提交后尽力登记进程内写入任务；进程退出或队列拒绝时可能丢失。 */
        AFTER_COMMIT
    }

    /** 方法显式值优先于类型显式值，均为 DEFAULT 时使用应用配置。 */
    Mode mode() default Mode.DEFAULT;

    /**
     * 返回用作审计操作资源的操作键。
     *
     * @return 用作审计操作资源的操作键
     */
    String operation();

    /**
     * 返回可选审计目标资源解析器。
     *
     * @return 可选审计目标资源解析器
     */
    Class<? extends AuditTargetResolver> targetResolver() default NoAuditTargetResolver.class;

    /** 从方法参数、Bean 或返回结果读取审计目标的表达式；与 `targetResolver` 和参数标记三选一。 */
    String target() default "";

    /** 表达式返回资源标识时使用的资源类型；表达式返回完整 Resource 时可为空。 */
    String resourceType() default "";

    /**
     * 返回可选结构化审计载荷解析器。
     *
     * @return 可选结构化审计载荷解析器
     */
    Class<? extends AuditPayloadFactory> payloadFactory() default NoAuditPayloadFactory.class;

    /** 从方法参数或返回结果读取 AuditPayload 的表达式；与 `payloadFactory` 二选一。 */
    String payload() default "";

}
