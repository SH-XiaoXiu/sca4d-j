package com.sca4d.app.server.authentication;

/** 定义动作认证 HTTP 接口共用的请求头。 */
public class AuthenticationHttpContract {

    /** 携带已经满足当前接口动作认证要求的单次验证标识。 */
    public static final String VERIFICATION_HEADER = "X-Verification";

    private AuthenticationHttpContract() {
    }
}
