package com.sca4d.tenant.request;

import com.sca4d.common.concurrent.VersionedRequest;
import com.sca4d.common.validation.Required;
import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/** 替换租户成员角色请求。 */
@Data
public class TenantMemberRolesRequest implements VersionedRequest {
    private List<Long> roleIds = new ArrayList<>();

    @Required
    private Long version;
}
