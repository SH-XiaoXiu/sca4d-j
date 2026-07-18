package com.sca4d.iam.passkey.request;

import com.sca4d.common.validation.SafeText;
import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** 创建浏览器绑定 Passkey registration 的展示名称请求。 */
@Getter
@Setter
@NoArgsConstructor
public class PasskeyRegistrationCreateRequest {

    @NotBlank
    @SafeText(max = 128)
    private String displayName;
}
