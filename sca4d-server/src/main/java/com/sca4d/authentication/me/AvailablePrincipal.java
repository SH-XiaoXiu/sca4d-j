package com.sca4d.authentication.me;

import com.sca4d.common.auth.PrincipalType;
import com.sca4d.iam.principal.PrincipalStatus;
import lombok.Data;
import lombok.experimental.Accessors;

/**
 * 当前 Subject 可用于后续请求主体选择的 Principal。
 */
@Data
@Accessors(chain = true)
public class AvailablePrincipal {

    private Long principalId;
    private PrincipalType principalType;
    private Long tenantId;
    private String displayName;
    private PrincipalStatus status;
}
