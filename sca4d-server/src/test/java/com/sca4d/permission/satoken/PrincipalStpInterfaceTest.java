package com.sca4d.permission.satoken;

import static org.assertj.core.api.Assertions.assertThat;

import com.sca4d.common.auth.Principal;
import com.sca4d.common.auth.PrincipalType;
import com.sca4d.common.permission.SessionPermission;
import com.sca4d.common.request.CurrentRequest;
import com.sca4d.common.request.CurrentRequestHolder;
import com.sca4d.test.TestSession;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.Set;

/** 验证 Sa-Token 从 Session 中读取当前 Principal 的角色和权限。 */
class PrincipalStpInterfaceTest {

    private final PrincipalStpInterface stpInterface = new PrincipalStpInterface();

    @AfterEach
    void tearDown() {
        CurrentRequestHolder.clear();
    }

    @Test
    void shouldExposeOnlyCurrentPrincipalPermission() {
        CurrentRequest request = CurrentRequest.authenticated(TestSession.of(1L));
        request.setPrincipal(Principal.of(10L, PrincipalType.DEFAULT, 1L, null));
        request.setSessionPermission(new SessionPermission(
                10L, Set.of("admin"), Set.of("demo:read")));
        CurrentRequestHolder.set(request);

        assertThat(stpInterface.getRoleList(1L, "login")).containsExactly("admin");
        assertThat(stpInterface.getPermissionList(1L, "login"))
                .containsExactly("demo:read");
    }

    @Test
    void shouldFailClosedWhenRequestPermissionDoesNotMatchPrincipal() {
        CurrentRequest request = CurrentRequest.authenticated(TestSession.of(1L));
        request.setPrincipal(Principal.of(10L, PrincipalType.DEFAULT, 1L, null));
        request.setSessionPermission(new SessionPermission(11L, Set.of("admin"), Set.of("demo:other")));
        CurrentRequestHolder.set(request);

        assertThat(stpInterface.getPermissionList(1L, "login")).isEmpty();
    }

    @Test
    void shouldFailClosedWhenPrincipalIdIsMissing() {
        CurrentRequest request = CurrentRequest.authenticated(TestSession.of(1L));
        request.setPrincipal(new Principal());
        request.setSessionPermission(new SessionPermission(10L, Set.of("admin"), Set.of("demo:read")));
        CurrentRequestHolder.set(request);

        assertThat(stpInterface.getPermissionList(1L, "login")).isEmpty();
        assertThat(stpInterface.getRoleList(1L, "login")).isEmpty();
    }

    @Test
    void shouldRejectWildcardPermissionAtModelBoundary() {
        Assertions.assertThatThrownBy(
                () -> new SessionPermission(10L, Set.of("admin"), Set.of("*")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("SessionPermission 禁止使用通配权限");
    }
}
