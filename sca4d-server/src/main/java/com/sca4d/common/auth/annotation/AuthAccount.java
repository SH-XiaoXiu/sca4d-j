package com.sca4d.common.auth.annotation;

import io.swagger.v3.oas.annotations.Parameter;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 注入当前会话完成认证的 Account。
 *
 * <p>Controller 参数类型为 {@code Account} 时注入账号；参数类型为 {@code Long} 时注入 accountId。
 */
@Target(ElementType.PARAMETER)
@Retention(RetentionPolicy.RUNTIME)
@Parameter(hidden = true)
public @interface AuthAccount {

    /**
     * 是否允许当前账号为空。
     *
     * @return true 表示未登录时注入 null；false 表示未登录时抛出异常
     */
    boolean nullable() default false;
}
