package com.sca4d.iam.principal;

import lombok.Data;
import lombok.experimental.Accessors;

/** PLATFORM Principal 当前绑定或可以选择的平台角色。 */
@Data
@Accessors(chain = true)
public class PlatformRole {
    private Long principalId;
    private Long roleId;
    private String roleCode;
    private String roleName;
}
