package com.sca4d.auth.federation;

import com.sca4d.core.api.enums.BaseEnum;

/** OAuth/OIDC Token Endpoint 接受 Client 凭据的方式。 */
public enum FederatedClientAuthentication implements BaseEnum {
    /** Authorization header 中使用 HTTP Basic。 */
    CLIENT_SECRET_BASIC,
    /** 表单正文中发送 client_id 和 client_secret。 */
    CLIENT_SECRET_POST,
    /** 表单正文发送 client_secret，其值是 Provider Driver 用已保存的签名材料即时签发的 JWT。 */
    CLIENT_SECRET_SIGNED_JWT,
    /** 不发送 Client Secret，仅允许带 PKCE 的公开客户端。 */
    ONE;

    /** @return 用于传输和持久化的认证方式值 */
    @Override
    public String value() {
        return name();
    }

    /** @return 认证方式的中文说明 */
    @Override
    public String description() {
        return switch (this) {
            case CLIENT_SECRET_BASIC -> "HTTP Basic";
            case CLIENT_SECRET_POST -> "请求正文";
            case CLIENT_SECRET_SIGNED_JWT -> "Driver 签发的 JWT";
            case ONE -> "不发送 Client Secret";
        };
    }
}
