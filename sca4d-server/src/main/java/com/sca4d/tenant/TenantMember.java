package com.sca4d.tenant;

import com.sca4d.iam.principal.PrincipalStatus;
import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/** 租户成员。 */
@Data
public class TenantMember {
    private Long membershipId;
    private Long principalId;
    private Long subjectId;
    private Long tenantId;
    private String displayName;
    private MembershipStatus membershipStatus;
    private Long membershipVersion;
    private PrincipalStatus principalStatus;
    private Long principalVersion;
    private List<RoleAssignment> roles = new ArrayList<>();
}
