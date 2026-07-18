package com.sca4d.tenant.request;

import com.sca4d.common.concurrent.VersionedRequest;
import com.sca4d.common.validation.Required;
import com.sca4d.iam.tenant.TenantStatus;
import lombok.Data;

/** 更新租户状态请求。 */
@Data
public class TenantStatusRequest implements VersionedRequest {
    @Required
    private TenantStatus status;

    @Required
    private Long version;
}
