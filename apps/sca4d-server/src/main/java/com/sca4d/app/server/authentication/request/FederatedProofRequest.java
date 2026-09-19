package com.sca4d.app.server.authentication.request;

import jakarta.validation.constraints.NotBlank;
import org.jetbrains.annotations.NotNull;

/** 接收回调完成后的一次性联邦授权标识。 */
public final class FederatedProofRequest {
    private String authorizationId;

    /** 创建空请求供 JSON 绑定。 */
    public FederatedProofRequest() {
    }

    /** @return completion id */
    @NotBlank
    public @NotNull String getAuthorizationId() {
        return authorizationId;
    }

    /** @param authorizationId completion id */
    public void setAuthorizationId(String authorizationId) {
        this.authorizationId = authorizationId;
    }
}
