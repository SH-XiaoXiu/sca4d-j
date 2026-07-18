package com.sca4d.authentication.email.request;

import com.sca4d.common.validation.SafeText;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;
import lombok.NoArgsConstructor;

/** 向 Authentication 提交邮件验证码 Proof 的请求。 */
@Data
@NoArgsConstructor
public class EmailCodeProofRequest {

    @NotBlank
    @SafeText(max = 128)
    private String challengeId;

    @NotBlank
    @SafeText(max = 32)
    private String code;

}
