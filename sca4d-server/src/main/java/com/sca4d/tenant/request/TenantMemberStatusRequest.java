package com.sca4d.tenant.request;

import com.sca4d.common.concurrent.VersionedRequest;
import com.sca4d.common.validation.Required;
import com.sca4d.tenant.MembershipStatus;
import lombok.Data;

/** 更新租户成员状态请求。 */
@Data
public class TenantMemberStatusRequest implements VersionedRequest {
    @Required
    private MembershipStatus status;

    @Required
    private Long version;
}
