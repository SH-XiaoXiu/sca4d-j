package com.sca4d.tenant.request;

import com.sca4d.common.concurrent.IdempotentRequest;
import com.sca4d.common.concurrent.VersionedRequest;
import com.sca4d.common.validation.Required;
import lombok.Data;

/** 将租户推进到不可恢复删除终态的并发与幂等请求。 */
@Data
public class TenantDeleteRequest implements VersionedRequest, IdempotentRequest {

    @Required
    private Long version;

    private String idempotencyKey;
}
