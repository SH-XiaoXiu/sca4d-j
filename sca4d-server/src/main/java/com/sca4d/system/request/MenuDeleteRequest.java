package com.sca4d.system.request;

import com.sca4d.common.concurrent.IdempotentRequest;
import com.sca4d.common.concurrent.VersionedRequest;
import com.sca4d.common.validation.Required;
import lombok.Data;

/** 删除一个系统菜单子树的并发与幂等请求。 */
@Data
public class MenuDeleteRequest implements VersionedRequest, IdempotentRequest {

    @Required
    private Long version;

    private String idempotencyKey;
}
