package com.sca4d.entity;

import com.mybatisflex.annotation.Table;
import com.sca4d.iam.principal.RoleStatus;
import lombok.Data;
import lombok.experimental.Accessors;

import java.time.OffsetDateTime;

/**
 * {@code iam_role} 保存的角色定义。
 *
 * <p>{@code code} 由平台内置角色或租户角色管理操作在所属范围内定义，创建后不可修改。
 */
@Data
@Accessors(chain = true)
@Table("iam_role")
public class IamRoleEntity {
    private Long id;
    private Long tenantId;
    private String code;
    private String name;
    private RoleStatus status;
    private Boolean builtIn;
    private OffsetDateTime createdAt;
    private OffsetDateTime updatedAt;
    private Long version;
    private Integer deleted;
}
