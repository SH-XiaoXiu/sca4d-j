package com.sca4d.iam;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.sca4d.common.auth.Principal;
import com.sca4d.common.auth.PrincipalType;
import com.sca4d.common.auth.Subject;
import com.sca4d.common.exception.BizException;
import com.sca4d.common.permission.SessionPermission;
import com.sca4d.common.request.CurrentRequest;
import com.sca4d.iam.principal.TenantPrincipalSelection;
import com.sca4d.test.TestSession;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

/** 验证租户请求按 tenantId 选择成员 Principal 并拒绝越权访问。 */
class TenantPrincipalSelectionTest {

    @Test
    void shouldSelectTenantPrincipalOnlyFromSessionSnapshot() {
        Principal principal = Principal.of(30L, PrincipalType.TENANT, 10L, 20L);
        CurrentRequest request = CurrentRequest.authenticated(TestSession.withAuthorization(
                new Subject(10L, "用户"),
                List.of(principal),
                Map.of(30L, new SessionPermission(30L, null, null))));

        Principal selected = new TenantPrincipalSelection()
                .select(request, new PrincipalType[] {PrincipalType.TENANT}, null, 20L, null, Map.of());

        assertThat(selected).isSameAs(principal);
    }

    @Test
    void shouldRejectTenantMissingFromSessionSnapshot() {
        CurrentRequest request = CurrentRequest.authenticated(TestSession.of(10L));

        assertThatThrownBy(() -> new TenantPrincipalSelection()
                .select(request, new PrincipalType[0], null, 20L, null, Map.of()))
                .isInstanceOfSatisfying(BizException.class, exception -> assertThat(exception.getMessageKey())
                        .isEqualTo("error.tenant.membership-unavailable"));
    }
}
