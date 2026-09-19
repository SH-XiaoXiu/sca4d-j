package com.sca4d.app.server.authentication;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/** 声明 Controller 接口执行前必须单次消费指定动作的认证结果。 */
@Target({ElementType.METHOD, ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
public @interface RequireAuthentication {

    /** 返回与认证尝试绑定且由当前接口消费的稳定动作编码。 */
    String value();
}
