package com.sca4d.role.request;

import com.sca4d.common.concurrent.VersionedRequest;
import com.sca4d.common.validation.DisplayName;
import com.sca4d.common.validation.Required;
import com.sca4d.iam.principal.RoleStatus;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/** 修改角色展示名称和启用状态的请求。 */
@Data
public class RoleUpdateRequest implements VersionedRequest {

    @NotBlank
    @DisplayName(max = 128)
    private String name;

    @Required
    private RoleStatus status;

    @Required
    private Long version;
}
