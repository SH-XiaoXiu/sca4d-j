package com.sca4d.role;

import com.sca4d.iam.principal.RoleStatus;
import lombok.Data;
import lombok.experimental.Accessors;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;

/** 平台或租户精确范围中的角色管理读取对象。 */
@Data
@Accessors(chain = true)
public class ManagedRole {

    private Long roleId;
    private Long tenantId;
    private String code;
    private String name;
    private RoleStatus status;
    private Boolean builtIn;
    private OffsetDateTime createdAt;
    private OffsetDateTime updatedAt;
    private Long version;
    private long principalCount;
    private long permissionCount;
    private List<ManagedPermission> permissions = new ArrayList<>();
}
