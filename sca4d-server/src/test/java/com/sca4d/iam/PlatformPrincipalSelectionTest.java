package com.sca4d.iam;

import static org.assertj.core.api.Assertions.assertThat;

import com.sca4d.common.auth.Principal;
import com.sca4d.common.auth.PrincipalType;
import com.sca4d.common.auth.Subject;
import com.sca4d.common.permission.SessionPermission;
import com.sca4d.common.request.CurrentRequest;
import com.sca4d.iam.principal.PlatformPrincipalSelection;
import com.sca4d.test.TestSession;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

/** 验证平台请求只选择可用的 PLATFORM Principal。 */
class PlatformPrincipalSelectionTest {

    @Test
    void shouldSelectPlatformPrincipalOnlyFromSessionSnapshot() {
        Principal principal = Principal.of(30L, PrincipalType.PLATFORM, 10L, null);
        CurrentRequest request = CurrentRequest.authenticated(TestSession.withAuthorization(
                new Subject(10L, "用户"),
                List.of(principal),
                Map.of(30L, new SessionPermission(30L, null, null))));

        Principal selected = new PlatformPrincipalSelection()
                .select(request, new PrincipalType[] {PrincipalType.PLATFORM}, null, null, null, Map.of());

        assertThat(selected).isSameAs(principal);
    }
}
