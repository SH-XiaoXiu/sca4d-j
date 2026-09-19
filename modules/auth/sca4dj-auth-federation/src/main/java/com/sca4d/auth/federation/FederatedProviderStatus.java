package com.sca4d.auth.federation;

import com.sca4d.core.api.enums.BaseEnum;

/** 外部登录 Provider 的启用状态。 */
public enum FederatedProviderStatus implements BaseEnum {
    /** Provider 不参与登录，也不能修改协议配置或删除。 */
    DISABLED,
    /** Provider 已通过完整配置检查，可以参与登录。 */
    ACTIVE;

    /** @return 用于传输和持久化的状态值 */
    @Override
    public String value() {
        return name();
    }

    /** @return 状态的中文说明 */
    @Override
    public String description() {
        return this == ACTIVE ? "启用" : "停用";
    }
}
