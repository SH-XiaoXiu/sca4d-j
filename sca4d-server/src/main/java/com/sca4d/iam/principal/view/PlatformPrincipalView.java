package com.sca4d.iam.principal.view;

import com.sca4d.iam.principal.PlatformPrincipal;
import com.sca4d.iam.principal.PrincipalStatus;
import lombok.Data;
import lombok.experimental.Accessors;
import org.jetbrains.annotations.NotNull;

import java.util.List;

/** 管理端可编辑的 PLATFORM Principal 摘要。 */
@Data
@Accessors(chain = true)
public class PlatformPrincipalView {
    private Long principalId;
    private String displayName;
    private PrincipalStatus status;
    private Long version;
    private List<PlatformRoleView> roles;

    /** @param principal 已聚合当前角色的平台主体 @return 管理端平台主体摘要 */
    public static @NotNull PlatformPrincipalView from(@NotNull PlatformPrincipal principal) {
        return new PlatformPrincipalView()
                .setPrincipalId(principal.getPrincipalId())
                .setDisplayName(principal.getDisplayName())
                .setStatus(principal.getStatus())
                .setVersion(principal.getVersion())
                .setRoles(principal.getRoles().stream().map(PlatformRoleView::from).toList());
    }
}
