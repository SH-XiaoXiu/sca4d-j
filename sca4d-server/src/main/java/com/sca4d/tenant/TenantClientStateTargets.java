package com.sca4d.tenant;

import com.sca4d.common.web.ClientStateTarget;

/** 租户模块写入客户端响应的固定状态目标。 */
public final class TenantClientStateTargets {

    /** PLATFORM Principal 可以访问的租户范围。 */
    public static final ClientStateTarget ACCESS = ClientStateTarget.of("tenant-access");

    private TenantClientStateTargets() {
    }
}
