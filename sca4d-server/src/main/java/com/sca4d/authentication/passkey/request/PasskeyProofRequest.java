package com.sca4d.authentication.passkey.request;

import com.fasterxml.jackson.databind.JsonNode;
import com.sca4d.common.validation.BizId;
import com.sca4d.common.validation.Required;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.jetbrains.annotations.NotNull;

/** 使用 assertion ID 和浏览器 PublicKeyCredential 响应创建 Passkey Proof。 */
@Getter
@Setter
@NoArgsConstructor
public class PasskeyProofRequest {

    @NotBlank
    @BizId(max = 64)
    private String assertionId;

    @Required
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
    private JsonNode credential;

    /** @return 只在当前 Proof 请求内交给 Yubico 解析的结构化响应 JSON */
    public @NotNull String responseJson() {
        return credential.toString();
    }

    @Override
    public @NotNull String toString() {
        return "PasskeyProofRequest[assertionId=" + assertionId + ", credential=protected]";
    }
}
