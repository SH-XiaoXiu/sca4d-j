package com.sca4d.authentication;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.sca4d.authentication.me.MePrincipalSelection;
import com.sca4d.common.auth.ForbiddenException;
import com.sca4d.common.auth.Principal;
import com.sca4d.common.auth.PrincipalType;
import com.sca4d.common.auth.Subject;
import com.sca4d.common.permission.SessionPermission;
import com.sca4d.common.request.CurrentRequest;
import com.sca4d.test.TestSession;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

/** 验证查询当前用户时只使用 Session 中已经选择的 Principal。 */
class MePrincipalSelectionTest {

    @Test
    void shouldUseDefaultPrincipalWhenTypeIsAbsent() {
        CurrentRequest request = CurrentRequest.authenticated(TestSession.of(10L));

        Principal selected = new MePrincipalSelection()
                .select(request, new PrincipalType[0], null, null, null, Map.of());

        assertThat(selected.getPrincipalId()).isEqualTo(10L);
        assertThat(selected.getPrincipalType()).isEqualTo(PrincipalType.DEFAULT);
    }

    @Test
    void shouldSelectTenantPrincipalForCurrentSubject() {
        Principal tenantPrincipal = Principal.of(30L, PrincipalType.TENANT, 10L, 20L);
        CurrentRequest request = CurrentRequest.authenticated(TestSession.withAuthorization(
                new Subject(10L, "用户"),
                List.of(tenantPrincipal),
                Map.of(30L, new SessionPermission(30L, null, null))));

        Principal selected = new MePrincipalSelection()
                .select(request, new PrincipalType[0], null, 20L, "TENANT", Map.of());

        assertThat(selected).isSameAs(tenantPrincipal);
    }

    @Test
    void shouldRejectTenantSelectionWithoutTenantId() {
        CurrentRequest request = CurrentRequest.authenticated(TestSession.of(10L));
        MePrincipalSelection selection = new MePrincipalSelection();

        assertThatThrownBy(() -> selection.select(
                request, new PrincipalType[0], null, null, "TENANT", Map.of()))
                .isInstanceOf(ForbiddenException.class);
    }
}
