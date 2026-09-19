package com.sca4d.iam.base.rbac;

import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.sca4d.core.api.authentication.SessionControl;
import com.sca4d.iam.identity.IdentityService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

class PlatformRbacSessionInvalidationTest {

    @Test
    @DisplayName("Invalidates Every Account Related To The Changed Principals Once")
    void invalidatesEveryAccountRelatedToTheChangedPrincipalsOnce() {
        IdentityService identities = mock(IdentityService.class);
        SessionControl sessions = mock(SessionControl.class);
        long firstPrincipal = 11L;
        long secondPrincipal = 12L;
        long firstAccount = 21L;
        long sharedAccount = 22L;
        long thirdAccount = 23L;
        when(identities.findLinkedAccountIds(List.of(firstPrincipal, secondPrincipal)))
                .thenReturn(
                        Map.of(
                                firstPrincipal,
                                List.of(firstAccount, sharedAccount),
                                secondPrincipal,
                                List.of(sharedAccount, thirdAccount)));
        PlatformRbacSessionInvalidation invalidation = new PlatformRbacSessionInvalidation(identities, sessions);

        invalidation.invalidate(
                PlatformRbacRepository.PLATFORM,
                List.of(firstPrincipal, secondPrincipal));

        verify(sessions).invalidateAccounts(
                argThat(
                        accountIds -> accountIds.size() == 3
                                && accountIds.containsAll(List.of(firstAccount, sharedAccount, thirdAccount))));
    }
}
