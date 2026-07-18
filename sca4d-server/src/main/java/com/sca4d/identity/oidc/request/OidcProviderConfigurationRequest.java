package com.sca4d.identity.oidc.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.List;

/** 创建或完整替换 OIDC Provider 配置的请求。 */
@Data
public class OidcProviderConfigurationRequest {

    @NotBlank
    @Size(max = 512)
    private String issuer;

    @NotBlank
    @Size(max = 256)
    private String clientId;

    @Size(max = 4096)
    @Schema(accessMode = Schema.AccessMode.WRITE_ONLY, format = "password")
    private String clientSecret;

    @Valid
    @NotEmpty
    @Size(max = 32)
    private List<@NotBlank @Pattern(regexp = "[A-Za-z0-9._:-]{1,128}") String> scopes;
}
