package com.sca4d.authentication;

/** 敏感操作提交已完成认证时使用的 HTTP Header。 */
public final class AuthenticationHeaders {

    /** 敏感操作提交 STEP_UP 认证 UUID 的请求头。 */
    public static final String AUTHENTICATION_ID = "X-Authentication-Id";

    /** 常量容器不允许实例化。 */
    private AuthenticationHeaders() {
    }
}
