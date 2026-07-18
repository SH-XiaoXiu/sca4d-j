package com.sca4d.common.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.sca4d.common.permission.SessionPermission;
import com.sca4d.common.request.CurrentRequest;
import com.sca4d.test.TestSession;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

/** 验证默认 Principal 从 Session 中读取且不会查询数据库。 */
class DefaultPrincipalSelectionTest {

    private final DefaultPrincipalSelection selection = new DefaultPrincipalSelection();

    @Test
    void shouldSelectDefaultPrincipalOnlyFromSession() {
        Principal principal = selection.select(
                CurrentRequest.authenticated(TestSession.of(1L)),
                new PrincipalType[0],
                null,
                null,
                null,
                Map.of());

        assertThat(principal.getPrincipalId()).isEqualTo(1L);
        assertThat(principal.getSubjectId()).isEqualTo(1L);
        assertThat(principal.getPrincipalType()).isEqualTo(PrincipalType.DEFAULT);
        assertThat(principal.getTenantId()).isNull();
    }

    @Test
    void shouldIgnoreRequestPrincipalAndReturnSessionPrincipal() {
        CurrentRequest request = CurrentRequest.authenticated(TestSession.of(1L));
        Principal current = Principal.fromSubject(1L, PrincipalType.DEFAULT, null, "Injected");
        request.setPrincipal(current);

        Principal selected = selection.select(
                request,
                new PrincipalType[] {PrincipalType.DEFAULT},
                null,
                null,
                null,
                Map.of());

        assertThat(selected).isNotSameAs(current);
        assertThat(selected.getDisplayName()).isEqualTo("Subject 1");
    }

    @Test
    void shouldRejectWhenSessionHasNoDefaultPrincipal() {
        Subject subject = new Subject(1L, "Alice");
        CurrentRequest request = CurrentRequest.authenticated(
                TestSession.withAuthorization(subject, List.of(), Map.<Long, SessionPermission>of()));

        assertThatThrownBy(() -> selection.select(
                request,
                new PrincipalType[] {PrincipalType.DEFAULT},
                null,
                null,
                null,
                Map.of()))
                .isInstanceOfSatisfying(ForbiddenException.class, exception -> assertThat(exception.getMessageKey())
                        .isEqualTo("error.authentication.default-principal-unavailable"));
    }

    @Test
    void shouldRejectExternalSelectionAndNonDefaultTypes() {
        CurrentRequest request = CurrentRequest.authenticated(TestSession.of(1L));

        assertThatThrownBy(() -> selection.select(
                request,
                new PrincipalType[0],
                2L,
                null,
                null,
                Map.of()))
                .isInstanceOf(ForbiddenException.class);
        assertThatThrownBy(() -> selection.select(
                request,
                new PrincipalType[] {PrincipalType.PLATFORM},
                null,
                null,
                null,
                Map.of()))
                .isInstanceOf(ForbiddenException.class);
    }
}
