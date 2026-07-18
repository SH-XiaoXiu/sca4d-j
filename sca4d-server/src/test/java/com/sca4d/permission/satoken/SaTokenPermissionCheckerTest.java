package com.sca4d.permission.satoken;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mockStatic;

import cn.dev33.satoken.stp.StpUtil;
import com.sca4d.common.auth.Principal;
import com.sca4d.common.auth.PrincipalType;
import com.sca4d.common.auth.Subject;
import com.sca4d.common.permission.SessionPermission;
import com.sca4d.common.request.CurrentRequest;
import com.sca4d.test.TestSession;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import java.util.List;
import java.util.Map;
import java.util.Set;

/** 验证 Sa-Token 权限检查器处理角色、权限和默认拒绝。 */
class SaTokenPermissionCheckerTest {

    @Test
    void shouldResolvePermissionBeforeDelegatingToSaToken() {
        Principal principal = Principal.of(10L, PrincipalType.DEFAULT, 1L, null);
        SessionPermission permission = new SessionPermission(10L, Set.of("admin"), Set.of("demo:read"));
        CurrentRequest request = CurrentRequest.authenticated(TestSession.withAuthorization(
                new Subject(1L, "用户"), List.of(principal), Map.of(10L, permission)));
        request.setPrincipal(principal);
        SaTokenPermissionChecker checker = new SaTokenPermissionChecker();

        try (MockedStatic<StpUtil> stp = mockStatic(StpUtil.class)) {
            checker.requirePermission(request, "demo:read");
            checker.requireRole(request, "admin");

            assertThat(request.getSessionPermission()).isSameAs(permission);
            stp.verify(() -> StpUtil.checkPermission("demo:read"));
            stp.verify(() -> StpUtil.checkRole("admin"));
        }
    }
}
