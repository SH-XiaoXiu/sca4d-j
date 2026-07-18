package com.sca4d.iam.passkey;

import org.jetbrains.annotations.NotNull;

/** 已单次消费 assertion ceremony 后交给 Passkey CredentialScheme 验证的请求和客户端响应。 */
public final class PasskeyProof {
    private final String requestJson;
    private final String responseJson;

    public PasskeyProof(@NotNull String requestJson, @NotNull String responseJson) {
        this.requestJson = requestJson;
        this.responseJson = responseJson;
    }

    public @NotNull String requestJson() {
        return requestJson;
    }

    public @NotNull String responseJson() {
        return responseJson;
    }

    @Override
    public @NotNull String toString() {
        return "PasskeyProof[request=protected, response=protected]";
    }
}
