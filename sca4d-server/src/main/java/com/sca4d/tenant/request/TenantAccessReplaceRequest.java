package com.sca4d.tenant.request;

import com.sca4d.common.access.AccessAction;
import com.sca4d.common.concurrent.IdempotentRequest;
import com.sca4d.common.concurrent.VersionedRequest;
import com.sca4d.common.validation.Required;
import jakarta.validation.Valid;
import lombok.Data;

import java.util.List;

/** 原子替换一个 PLATFORM Principal 完整租户 ACL 的请求。 */
@Data
public class TenantAccessReplaceRequest implements VersionedRequest, IdempotentRequest {

    private AccessAction defaultAction;

    @Valid
    private List<@Required TenantAccessGrantRequest> grants = List.of();

    @Required
    private Long version;

    private String idempotencyKey;
}
