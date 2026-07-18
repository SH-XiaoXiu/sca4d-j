package com.sca4d.iam.principal.request;

import com.sca4d.common.concurrent.IdempotentRequest;
import com.sca4d.common.validation.DisplayName;
import com.sca4d.common.validation.Required;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/** 从已有 GLOBAL Account 创建无角色、无租户 ACL 的平台操作员请求。 */
@Data
public class PlatformOperatorCreateRequest implements IdempotentRequest {

    @Required
    private Long accountId;

    @NotBlank
    @DisplayName
    private String displayName;

    private String idempotencyKey;
}
