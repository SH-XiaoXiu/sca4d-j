package com.sca4d.tenant.request;

import com.sca4d.common.web.PageQuery;
import com.sca4d.tenant.MembershipStatus;
import lombok.Data;
import lombok.EqualsAndHashCode;

/** 租户成员分页查询。 */
@Data
@EqualsAndHashCode(callSuper = true)
public class TenantMemberPageRequest extends PageQuery {
    private String search;
    private MembershipStatus status;
}
