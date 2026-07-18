package com.sca4d.tenant.request;

import com.sca4d.common.access.AccessAction;
import com.sca4d.common.validation.Required;
import lombok.Data;

/** 一个具体租户的最高访问等级输入。 */
@Data
public class TenantAccessGrantRequest {

    @Required
    private Long tenantId;

    @Required
    private AccessAction accessAction;
}
