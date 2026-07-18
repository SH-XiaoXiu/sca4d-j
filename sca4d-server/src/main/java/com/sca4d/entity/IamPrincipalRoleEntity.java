package com.sca4d.entity;

import com.mybatisflex.annotation.Table;
import lombok.Data;
import lombok.experimental.Accessors;

import java.time.OffsetDateTime;

/** {@code iam_principal_role} 保存的 Principal 与 Role 关系。 */
@Data
@Accessors(chain = true)
@Table("iam_principal_role")
public class IamPrincipalRoleEntity {
    private Long id;
    private Long principalId;
    private Long roleId;
    private OffsetDateTime createdAt;
}
