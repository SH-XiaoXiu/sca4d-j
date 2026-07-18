package com.sca4d.tenant.view;

import com.sca4d.iam.tenant.TenantStatus;
import com.sca4d.iam.tenant.TenantType;
import com.sca4d.tenant.Tenant;
import lombok.Data;
import lombok.experimental.Accessors;
import org.jetbrains.annotations.NotNull;

import java.time.OffsetDateTime;

/**
 * 管理端租户展示对象。
 *
 * <p>{@code code} 原样展示租户当前业务编码；历史编码只作为永久占用数据保存，不由普通列表重复返回。
 */
@Data
@Accessors(chain = true)
public class TenantView {

    private Long tenantId;
    private String code;
    private String name;
    private TenantType tenantType;
    private TenantStatus status;
    private OffsetDateTime createdAt;
    private OffsetDateTime updatedAt;
    private Long version;

    /**
     * 将租户读取对象转换为管理端响应结构。
     *
     * @param tenant 已从 IAM 表读取的租户
     * @return 不暴露持久化实体的租户展示对象
     */
    public static @NotNull TenantView from(@NotNull Tenant tenant) {
        return new TenantView()
                .setTenantId(tenant.getTenantId())
                .setCode(tenant.getCode())
                .setName(tenant.getName())
                .setTenantType(tenant.getTenantType())
                .setStatus(tenant.getStatus())
                .setCreatedAt(tenant.getCreatedAt())
                .setUpdatedAt(tenant.getUpdatedAt())
                .setVersion(tenant.getVersion());
    }
}
