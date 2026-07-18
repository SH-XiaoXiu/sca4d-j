package com.sca4d.entity;

import com.mybatisflex.annotation.Table;
import lombok.Data;
import lombok.experimental.Accessors;

import java.time.OffsetDateTime;

/** {@code iam_role_permission} 保存的 Role 与 Permission 关系。 */
@Data
@Accessors(chain = true)
@Table("iam_role_permission")
public class IamRolePermissionEntity {
    private Long id;
    private Long roleId;
    private Long permissionId;
    private OffsetDateTime createdAt;
}
