package com.sca4d.entity;

import com.mybatisflex.annotation.Table;
import com.sca4d.iam.tenant.TenantStatus;
import com.sca4d.iam.tenant.TenantType;
import lombok.Data;
import lombok.experimental.Accessors;

import java.time.OffsetDateTime;

/**
 * {@code iam_tenant} 保存的租户事实。
 *
 * <p>{@code code} 是平台可修改的当前业务标识，不表示有限枚举；所有曾用值由 {@code iam_tenant_code} 永久保存。
 */
@Data
@Accessors(chain = true)
@Table("iam_tenant")
public class IamTenantEntity {
    private Long id;
    private String code;
    private String name;
    private TenantType tenantType;
    private TenantStatus status;
    private OffsetDateTime createdAt;
    private OffsetDateTime updatedAt;
    private Long version;
    private Integer deleted;
}
