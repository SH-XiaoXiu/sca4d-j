package com.sca4d.authentication;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.sca4d.authentication.password.PasswordAuthenticationProof;
import com.sca4d.identity.external.ExternalIdentityAuthenticationProof;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

/** 验证认证方式目录的唯一性、能力声明和类型化读取。 */
class AuthMethodCatalogTest {

    @Test
    void shouldExposeRegisteredMethodByTypeAndProofClass() {
        @SuppressWarnings("unchecked")
        AuthMethod<PasswordAuthenticationProof> password = mock(AuthMethod.class);
        when(password.type()).thenReturn(AuthMethodType.PASSWORD);
        when(password.proofClass()).thenReturn(PasswordAuthenticationProof.class);
        when(password.capabilities()).thenReturn(Set.of(AuthMethodCapability.LOGIN));

        AuthMethodCatalog catalog = new AuthMethodCatalog(List.of(password));

        assertThat(catalog.methods()).containsExactly(password);
        assertThat(catalog.require(AuthMethodType.PASSWORD)).isSameAs(password);
        assertThat(catalog.require(AuthMethodType.PASSWORD, PasswordAuthenticationProof.class))
                .isSameAs(password);
    }

    @Test
    void shouldRejectMethodWithoutCapabilities() {
        @SuppressWarnings("unchecked")
        AuthMethod<PasswordAuthenticationProof> password = mock(AuthMethod.class);
        when(password.type()).thenReturn(AuthMethodType.PASSWORD);
        when(password.proofClass()).thenReturn(PasswordAuthenticationProof.class);
        when(password.capabilities()).thenReturn(Set.of());

        assertThatThrownBy(() -> new AuthMethodCatalog(List.of(password)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("至少一种能力");
    }

    @Test
    void shouldAllowDifferentExternalProtocolsToReuseVersionProof() {
        @SuppressWarnings("unchecked")
        AuthMethod<ExternalIdentityAuthenticationProof> oidc = mock(AuthMethod.class);
        when(oidc.type()).thenReturn(AuthMethodType.OIDC);
        when(oidc.proofClass()).thenReturn(ExternalIdentityAuthenticationProof.class);
        when(oidc.capabilities()).thenReturn(Set.of(AuthMethodCapability.LOGIN));
        @SuppressWarnings("unchecked")
        AuthMethod<ExternalIdentityAuthenticationProof> oauth2 = mock(AuthMethod.class);
        when(oauth2.type()).thenReturn(AuthMethodType.OAUTH2);
        when(oauth2.proofClass()).thenReturn(ExternalIdentityAuthenticationProof.class);
        when(oauth2.capabilities()).thenReturn(Set.of(AuthMethodCapability.LOGIN));

        AuthMethodCatalog catalog = new AuthMethodCatalog(List.of(oidc, oauth2));

        assertThat(catalog.require(AuthMethodType.OIDC, ExternalIdentityAuthenticationProof.class))
                .isSameAs(oidc);
        assertThat(catalog.require(AuthMethodType.OAUTH2, ExternalIdentityAuthenticationProof.class))
                .isSameAs(oauth2);
    }
}
