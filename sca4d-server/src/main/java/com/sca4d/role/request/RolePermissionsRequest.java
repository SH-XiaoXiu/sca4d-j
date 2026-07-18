package com.sca4d.role.request;

import com.sca4d.common.concurrent.VersionedRequest;
import com.sca4d.common.validation.Required;
import lombok.Data;

import java.util.List;

/** 原子替换自定义角色全部权限关系的请求。 */
@Data
public class RolePermissionsRequest implements VersionedRequest {

    @Required
    private List<@Required Long> permissionIds;

    @Required
    private Long version;
}
