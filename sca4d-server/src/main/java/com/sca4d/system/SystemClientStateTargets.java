package com.sca4d.system;

import com.sca4d.common.web.ClientStateTarget;

/** 系统管理模块写入客户端响应的固定状态目标。 */
public final class SystemClientStateTargets {

    /** 管理端菜单和当前 Principal 可见导航。 */
    public static final ClientStateTarget MENUS = ClientStateTarget.of("menus");
    /** 系统与租户字典。 */
    public static final ClientStateTarget DICTIONARIES = ClientStateTarget.of("dictionaries");

    private SystemClientStateTargets() {
    }
}
