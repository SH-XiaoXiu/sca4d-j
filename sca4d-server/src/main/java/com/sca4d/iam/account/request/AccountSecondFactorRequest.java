package com.sca4d.iam.account.request;

import com.sca4d.common.concurrent.VersionedRequest;
import com.sca4d.common.validation.Required;
import lombok.Data;

/** 修改 Account 第二因子要求的版本化请求。 */
@Data
public class AccountSecondFactorRequest implements VersionedRequest {

    @Required
    private Boolean required;

    @Required
    private Long version;
}
