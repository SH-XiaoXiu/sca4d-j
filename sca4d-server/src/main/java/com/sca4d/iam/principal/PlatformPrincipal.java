package com.sca4d.iam.principal;

import lombok.Data;
import lombok.experimental.Accessors;

import java.util.ArrayList;
import java.util.List;

/** 平台执行主体及其当前平台角色绑定。 */
@Data
@Accessors(chain = true)
public class PlatformPrincipal {
    private Long principalId;
    private Long subjectId;
    private String displayName;
    private PrincipalStatus status;
    private Long version;
    private List<PlatformRole> roles = new ArrayList<>();
}
