package com.sca4d.entity;

import com.mybatisflex.annotation.Table;
import com.sca4d.common.auth.PrincipalType;
import com.sca4d.iam.principal.PrincipalStatus;
import lombok.Data;
import lombok.experimental.Accessors;

import java.time.OffsetDateTime;

/** {@code iam_principal} 保存的业务执行主体事实。 */
@Data
@Accessors(chain = true)
@Table("iam_principal")
public class IamPrincipalEntity {
    private Long id;
    private Long subjectId;
    private Long tenantId;
    private Long membershipId;
    private PrincipalType principalType;
    private String displayName;
    private PrincipalStatus status;
    private OffsetDateTime createdAt;
    private OffsetDateTime updatedAt;
    private Long version;
    private Integer deleted;
}
