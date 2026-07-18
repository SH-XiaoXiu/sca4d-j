package com.sca4d.authentication.me.service;

import com.sca4d.authentication.me.AvailablePrincipal;
import com.sca4d.authentication.me.MeService;
import com.sca4d.authentication.me.MeSnapshot;
import com.sca4d.common.auth.Principal;
import com.sca4d.common.auth.Subject;
import com.sca4d.common.permission.PermissionChecker;
import com.sca4d.common.permission.SessionPermission;
import com.sca4d.common.request.CurrentRequest;
import com.sca4d.iam.principal.PrincipalStatus;
import com.sca4d.iam.profile.DefaultProfile;
import com.sca4d.iam.profile.service.DefaultProfileService;
import com.sca4d.system.MenuService;
import com.sca4d.system.NavigationMenu;
import com.sca4d.tenant.TenantService;
import lombok.RequiredArgsConstructor;
import org.jetbrains.annotations.NotNull;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

/**
 * 当前登录用户运行时状态查询服务。
 */
@Service
@RequiredArgsConstructor
public class DefaultMeService implements MeService {

    private final PermissionChecker permissionChecker;
    private final DefaultProfileService defaultProfileService;
    private final TenantService tenantService;
    private final MenuService menuService;

    @Override
    public @NotNull MeSnapshot snapshot(@NotNull CurrentRequest currentRequest) {
        // Subject、Principal 和权限只读取当前请求已恢复的完整 Session，不为 /me 增加 IAM 授权回源。
        Subject subject = currentRequest.requireSubject();
        // DEFAULT Profile 固定执行一条查询，为 /me 提供头像引用与版本，不加载 Credential 或其他 Principal。
        DefaultProfile defaultProfile = defaultProfileService.require(subject.getSubjectId());
        SessionPermission permission = permissionChecker.require(currentRequest);
        List<AvailablePrincipal> availablePrincipals = new ArrayList<>();
        for (Principal principal : currentRequest.requireSession().getPrincipals()) {
            availablePrincipals.add(new AvailablePrincipal()
                    .setPrincipalId(principal.requirePrincipalId())
                    .setPrincipalType(principal.getPrincipalType())
                    .setTenantId(principal.getTenantId())
                    .setDisplayName(principal.getDisplayName())
                    .setStatus(PrincipalStatus.ACTIVE));
        }
        Principal principal = currentRequest.getPrincipal();
        // /me 另固定执行一条租户成员关系查询和一条菜单整树查询；次数不随 Principal、租户或菜单数量增长。
        List<NavigationMenu> navigation = principal == null
                ? List.of()
                : menuService.navigation(principal, permission);
        // Controller 只接收认证数据快照，HTTP/STOMP 请求数据继续停留在共用请求态中。
        return new MeSnapshot(
                subject,
                currentRequest.requireAccount(),
                defaultProfile,
                principal,
                currentRequest.getSession(),
                permission,
                availablePrincipals,
                tenantService.memberships(subject.getSubjectId()),
                navigation);
    }
}
