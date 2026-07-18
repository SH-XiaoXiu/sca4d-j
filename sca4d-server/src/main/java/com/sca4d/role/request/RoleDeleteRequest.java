package com.sca4d.role.request;

import com.sca4d.common.concurrent.IdempotentRequest;
import com.sca4d.common.concurrent.VersionedRequest;
import com.sca4d.common.validation.Required;
import lombok.Data;

/** 删除未被 Principal 使用的自定义角色时提交的并发和幂等数据。 */
@Data
public class RoleDeleteRequest implements VersionedRequest, IdempotentRequest {

    @Required
    private Long version;

    private String idempotencyKey;
}
