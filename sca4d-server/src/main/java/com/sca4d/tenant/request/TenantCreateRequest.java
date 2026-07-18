package com.sca4d.tenant.request;

import com.sca4d.common.concurrent.IdempotentRequest;
import com.sca4d.common.validation.BizId;
import com.sca4d.common.validation.DisplayName;
import com.sca4d.common.validation.Required;
import com.sca4d.iam.tenant.TenantType;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * 创建租户请求。
 *
 * <p>{@code code} 由平台管理员为新租户分配，创建后不可修改，租户删除后也不可被其他租户复用。
 */
@Data
public class TenantCreateRequest implements IdempotentRequest {
    @NotBlank
    @BizId(max = 64)
    private String code;

    @NotBlank
    @DisplayName
    private String name;

    @Required
    private TenantType tenantType = TenantType.DEFAULT;

    private String idempotencyKey;
}
