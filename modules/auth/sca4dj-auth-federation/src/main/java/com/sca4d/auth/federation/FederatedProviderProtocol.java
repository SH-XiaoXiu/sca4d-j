package com.sca4d.auth.federation;

import com.sca4d.core.api.enums.BaseEnum;

/** 外部登录 Provider 使用的协议类型。创建后保持不变。 */
public enum FederatedProviderProtocol implements BaseEnum {
    /** 使用 OpenID Connect 身份断言。 */
    OIDC,
    /** 使用 OAuth 2.0 授权码和用户资料接口。 */
    OAUTH2;

    /** @return 用于传输和持久化的协议值 */
    @Override
    public String value() {
        return name();
    }

    /** @return 协议的中文说明 */
    @Override
    public String description() {
        return this == OIDC ? "OpenID Connect" : "OAuth 2.0";
    }
}
