package com.sca4d.tenant.request;

import com.sca4d.common.concurrent.VersionedRequest;
import com.sca4d.common.validation.BizId;
import com.sca4d.common.validation.DisplayName;
import com.sca4d.common.validation.Required;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/** 更新租户资料请求。 */
@Data
public class TenantUpdateRequest implements VersionedRequest {
    @NotBlank
    @BizId(max = 64)
    private String code;

    @NotBlank
    @DisplayName
    private String name;

    @Required
    private Long version;
}
