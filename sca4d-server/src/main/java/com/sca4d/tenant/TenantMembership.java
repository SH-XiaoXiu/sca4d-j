package com.sca4d.tenant;

import com.sca4d.iam.tenant.TenantStatus;
import com.sca4d.iam.tenant.TenantType;
import lombok.Data;

/** 当前 Subject 的租户成员关系。 */
@Data
public class TenantMembership {
    private Long membershipId;
    private Long principalId;
    private Long tenantId;
    private String tenantCode;
    private String tenantName;
    private TenantType tenantType;
    private TenantStatus tenantStatus;
    private MembershipStatus membershipStatus;
    private Long version;
}
