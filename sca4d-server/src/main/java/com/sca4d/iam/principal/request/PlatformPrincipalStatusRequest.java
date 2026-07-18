package com.sca4d.iam.principal.request;

import com.sca4d.common.concurrent.VersionedRequest;
import com.sca4d.common.validation.Required;
import com.sca4d.iam.principal.PrincipalStatus;
import lombok.Data;

/** 修改 PLATFORM Principal 状态的请求。 */
@Data
public class PlatformPrincipalStatusRequest implements VersionedRequest {

    @Required
    private PrincipalStatus status;

    @Required
    private Long version;
}
