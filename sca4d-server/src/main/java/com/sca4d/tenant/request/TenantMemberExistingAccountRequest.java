package com.sca4d.tenant.request;

import com.sca4d.common.concurrent.IdempotentRequest;
import com.sca4d.common.validation.DisplayName;
import com.sca4d.common.validation.Required;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/** 将已有 Account 所属 Subject 加入租户成员的请求。 */
@Data
public class TenantMemberExistingAccountRequest implements IdempotentRequest {

    @Required
    private Long accountId;

    @NotBlank
    @DisplayName
    private String displayName;

    private List<Long> roleIds = new ArrayList<>();

    private String idempotencyKey;
}
