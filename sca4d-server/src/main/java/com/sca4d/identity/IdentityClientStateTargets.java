package com.sca4d.identity;

import com.sca4d.common.web.ClientStateTarget;

/** 外部身份模块写入客户端响应的固定状态目标。 */
public final class IdentityClientStateTargets {

    /** IdentityProvider 管理列表和认证方式目录。 */
    public static final ClientStateTarget PROVIDERS = ClientStateTarget.of("identity-providers");

    private IdentityClientStateTargets() {
    }
}
