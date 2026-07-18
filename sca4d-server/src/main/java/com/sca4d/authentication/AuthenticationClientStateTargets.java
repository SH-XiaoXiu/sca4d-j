package com.sca4d.authentication;

import com.sca4d.common.web.ClientStateTarget;

/** 认证模块写入客户端响应的固定状态目标。 */
public final class AuthenticationClientStateTargets {

    /** 当前登录账号、Principal、权限和导航启动状态。 */
    public static final ClientStateTarget ME = ClientStateTarget.of("me");

    private AuthenticationClientStateTargets() {
    }
}
