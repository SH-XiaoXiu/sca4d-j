package com.sca4d.entity;

import com.mybatisflex.annotation.Table;
import com.sca4d.common.permission.PermissionType;
import lombok.Data;
import lombok.experimental.Accessors;

import java.time.OffsetDateTime;

/**
 * {@code iam_permission} 保存的权限定义。
 *
 * <p>{@code code} 只来自业务模块代码权限目录并由 Bootstrap 同步，数据库不独立发明权限编码。
 */
@Data
@Accessors(chain = true)
@Table("iam_permission")
public class IamPermissionEntity {
    private Long id;
    private Long parentId;
    private String code;
    private String nameKey;
    private PermissionType permissionType;
    private Integer sortOrder;
    private Boolean grantedByDefault;
    private OffsetDateTime createdAt;
    private OffsetDateTime updatedAt;
    private Long version;
    private Integer deleted;
}
