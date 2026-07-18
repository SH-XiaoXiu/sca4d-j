package com.sca4d.tenant.request;

import com.sca4d.common.web.PageQuery;
import com.sca4d.iam.tenant.TenantStatus;
import lombok.Data;
import lombok.EqualsAndHashCode;

/** 租户分页查询。 */
@Data
@EqualsAndHashCode(callSuper = true)
public class TenantPageRequest extends PageQuery {
    private String search;
    private TenantStatus status;
}
