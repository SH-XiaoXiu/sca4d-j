package com.sca4d.entity;

import com.mybatisflex.annotation.Table;
import com.sca4d.tenant.MembershipStatus;
import lombok.Data;
import lombok.experimental.Accessors;

import java.time.OffsetDateTime;

/** {@code iam_tenant_membership} 保存的 Subject 与 Tenant 成员关系。 */
@Data
@Accessors(chain = true)
@Table("iam_tenant_membership")
public class IamTenantMembershipEntity {
    private Long id;
    private Long subjectId;
    private Long tenantId;
    private MembershipStatus status;
    private OffsetDateTime joinedAt;
    private OffsetDateTime createdAt;
    private OffsetDateTime updatedAt;
    private Long version;
    private Integer deleted;
}
