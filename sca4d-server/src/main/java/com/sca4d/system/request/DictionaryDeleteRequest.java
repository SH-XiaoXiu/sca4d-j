package com.sca4d.system.request;

import com.sca4d.common.concurrent.IdempotentRequest;
import com.sca4d.common.concurrent.VersionedRequest;
import com.sca4d.common.validation.Required;
import lombok.Data;

/** 删除可配置字典类型或字典项的并发与幂等请求。 */
@Data
public class DictionaryDeleteRequest implements VersionedRequest, IdempotentRequest {

    @Required
    private Long version;

    private String idempotencyKey;
}
