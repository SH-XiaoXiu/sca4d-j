package com.sca4d.iam.account.request;

import com.sca4d.common.validation.Required;
import com.sca4d.iam.account.AccountStatus;
import lombok.Data;

/** 修改 GLOBAL Account 登录状态。 */
@Data
public class ManagedAccountStatusRequest {
    @Required
    private AccountStatus status;
}
