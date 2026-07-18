package com.sca4d.tenant;

import com.sca4d.iam.tenant.TenantStatus;
import com.sca4d.iam.tenant.TenantType;
import lombok.Data;

import java.time.OffsetDateTime;

/**
 * 租户。
 *
 * <p>{@code code} 表示当前租户业务编码，不表示有限状态或类型；修改后的历史值仍由数据库永久占用。
 */
@Data
public class Tenant {
    private Long tenantId;
    private String code;
    private String name;
    private TenantType tenantType;
    private TenantStatus status;
    private OffsetDateTime createdAt;
    private OffsetDateTime updatedAt;
    private Long version;
}
