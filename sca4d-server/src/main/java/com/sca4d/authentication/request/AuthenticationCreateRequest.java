package com.sca4d.authentication.request;

import com.sca4d.authentication.AuthenticationPurpose;
import com.sca4d.common.auth.AccountScopeType;
import com.sca4d.common.validation.BizId;
import com.sca4d.common.validation.Required;
import com.sca4d.common.validation.SafeText;
import lombok.Data;
import lombok.NoArgsConstructor;

/** 创建 Authentication 的输入。 */
@Data
@NoArgsConstructor
public class AuthenticationCreateRequest {
    @Required
    private AuthenticationPurpose purpose;

    private AccountScopeType scopeType;

    @BizId(max = 64)
    private String tenantCode;

    @BizId(max = 128)
    private String deviceId;

    @SafeText(max = 128)
    private String terminalName;
}
