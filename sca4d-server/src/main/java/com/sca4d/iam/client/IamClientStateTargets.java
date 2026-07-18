package com.sca4d.iam.client;

import com.sca4d.common.web.ClientStateTarget;

/** IAM 模块写入客户端响应的固定状态目标。 */
public final class IamClientStateTargets {

    /** Subject 下的登录账号和安全材料。 */
    public static final ClientStateTarget ACCOUNTS = ClientStateTarget.of("accounts");

    private IamClientStateTargets() {
    }
}
