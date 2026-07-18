package com.sca4d.iam.passkey.request;

import com.fasterxml.jackson.databind.JsonNode;
import com.sca4d.common.validation.BizId;
import com.sca4d.common.validation.Required;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.jetbrains.annotations.NotNull;

/** 使用短期 registration 和认证器 attestation 创建 Passkey Credential。 */
@Getter
@Setter
@NoArgsConstructor
public class PasskeyCredentialCreateRequest {

    @NotBlank
    @BizId(max = 64)
    private String registrationId;

    @Required
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
    private JsonNode credential;

    /** @return 只在当前创建请求内交给 Yubico 解析的结构化响应 JSON */
    public @NotNull String responseJson() {
        return credential.toString();
    }

    @Override
    public @NotNull String toString() {
        return "PasskeyCredentialCreateRequest[registrationId=" + registrationId + ", credential=protected]";
    }
}
