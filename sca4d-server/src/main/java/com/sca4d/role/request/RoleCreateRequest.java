package com.sca4d.role.request;

import com.sca4d.common.concurrent.IdempotentRequest;
import com.sca4d.common.validation.BizId;
import com.sca4d.common.validation.DisplayName;
import com.sca4d.common.validation.Required;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

import java.util.List;

/** 创建自定义角色及其初始权限集合的请求。 */
@Data
public class RoleCreateRequest implements IdempotentRequest {

    @NotBlank
    @BizId(max = 64)
    private String code;

    @NotBlank
    @DisplayName(max = 128)
    private String name;

    @Required
    private List<@Required Long> permissionIds;

    private String idempotencyKey;
}
