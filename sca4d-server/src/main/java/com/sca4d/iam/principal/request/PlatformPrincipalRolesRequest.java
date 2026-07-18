package com.sca4d.iam.principal.request;

import com.sca4d.common.concurrent.VersionedRequest;
import com.sca4d.common.validation.Required;
import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/** 替换 PLATFORM Principal 角色集合的请求。 */
@Data
public class PlatformPrincipalRolesRequest implements VersionedRequest {

    private List<Long> roleIds = new ArrayList<>();

    @Required
    private Long version;
}
