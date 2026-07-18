package com.sca4d.identity.oauth2.request;

import com.sca4d.identity.oauth2.OAuth2ProfileMapping;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;
import org.jetbrains.annotations.NotNull;

/** 配置 OAuth2 Profile 通用身份字段 JSONPath 的请求。 */
@Data
public class OAuth2ProfileMappingRequest {

    @NotBlank
    @Size(max = 256)
    private String subjectPath;

    @Size(max = 256)
    private String displayNamePath;

    @Size(max = 256)
    private String emailPath;

    @Size(max = 256)
    private String emailVerifiedPath;

    @Size(max = 256)
    private String avatarPath;

    /** @return 不包含 HTTP 请求对象的 Profile 映射输入 */
    public @NotNull OAuth2ProfileMapping toMapping() {
        return new OAuth2ProfileMapping(
                subjectPath, displayNamePath, emailPath, emailVerifiedPath, avatarPath);
    }
}
