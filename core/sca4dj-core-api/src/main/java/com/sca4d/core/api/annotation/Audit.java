package com.sca4d.core.api.annotation;

import com.sca4d.core.api.audit.AuditPayloadFactory;
import com.sca4d.core.api.audit.AuditTargetResolver;
import com.sca4d.core.api.audit.NoAuditPayloadFactory;
import com.sca4d.core.api.audit.NoAuditTargetResolver;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/** 声明调用需要记录成功、拒绝或失败的审计结果。 */
@Documented
@Target({ElementType.TYPE, ElementType.METHOD})
@Retention(RetentionPolicy.RUNTIME)
public @interface Audit {

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

    /**
     * 返回可选结构化审计载荷解析器。
     *
     * @return 可选结构化审计载荷解析器
     */
    Class<? extends AuditPayloadFactory> payloadFactory() default NoAuditPayloadFactory.class;

}
