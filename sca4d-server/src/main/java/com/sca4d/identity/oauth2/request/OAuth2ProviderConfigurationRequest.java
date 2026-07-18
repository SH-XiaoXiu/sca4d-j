package com.sca4d.identity.oauth2.request;

import com.sca4d.common.validation.Required;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.List;

/** 创建或完整替换标准 OAuth2 Provider 配置的请求。 */
@Data
public class OAuth2ProviderConfigurationRequest {

    @NotBlank
    @Size(max = 1024)
    private String authorizationUri;

    @NotBlank
    @Size(max = 1024)
    private String tokenUri;

    @NotBlank
    @Size(max = 1024)
    private String profileUri;

    @Size(max = 1024)
    private String auxiliaryProfileUri;

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

    @Valid
    @Required
    private OAuth2ProfileMappingRequest profileMapping;
}
