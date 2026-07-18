package com.sca4d.entity;

import com.mybatisflex.annotation.Table;
import com.sca4d.common.access.AccessAction;
import lombok.Data;
import lombok.experimental.Accessors;

import java.time.OffsetDateTime;

/** PLATFORM Principal 对租户业务模块资源的最高访问等级数据。 */
@Data
@Accessors(chain = true)
@Table("tenant_access_grant")
public class TenantAccessGrantEntity {
    private Long id;
    private Long principalId;
    private Long tenantId;
    private AccessAction accessAction;
    private OffsetDateTime createdAt;
}
