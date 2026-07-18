package com.sca4d.role.request;

import com.sca4d.common.web.PageQuery;
import com.sca4d.iam.principal.RoleStatus;
import lombok.Data;
import lombok.EqualsAndHashCode;

/** 角色分页搜索和启用状态筛选。 */
@Data
@EqualsAndHashCode(callSuper = true)
public class RolePageRequest extends PageQuery {

    private String search;
    private RoleStatus status;
}
