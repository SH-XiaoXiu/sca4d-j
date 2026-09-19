package com.sca4d.auth.session;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.sca4d.auth.session.codec.PrincipalIdLoaders;
import com.sca4d.auth.session.codec.PrincipalLoaders;
import com.sca4d.auth.session.codec.ResourceIdLoaders;
import com.sca4d.auth.session.codec.ResourceLoaders;
import com.sca4d.auth.testing.SessionTestFixture;
import com.sca4d.core.api.authentication.Session;
import com.sca4d.core.api.authentication.SessionIdentity;
import com.sca4d.core.api.authentication.principalsession.ResourceIdSet;
import com.sca4d.core.api.identity.Principal;
import com.sca4d.core.api.resource.Resource;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

class SessionRefreshServiceTest {

    @Test
    @DisplayName("Preserves Authentication And Lifetime While Reloading Identity And Resources")
    void preservesAuthenticationAndLifetimeWhileReloadingIdentityAndResources() {
        Session previous = SessionTestFixture.sessionWithCompactIds(
                SessionTestFixture.NOW.plusSeconds(3600),
                2);
        SessionIdentity identity = new SessionIdentity(previous.subject(), previous.account());
        SessionIdentityLoadService identities = mock(SessionIdentityLoadService.class);
        PrincipalLoaders principals = mock(PrincipalLoaders.class);
        PrincipalIdLoaders principalIds = mock(PrincipalIdLoaders.class);
        ResourceLoaders resources = mock(ResourceLoaders.class);
        ResourceIdLoaders resourceIds = mock(ResourceIdLoaders.class);
        when(identities.load(previous.account())).thenReturn(identity);
        when(principals.load(identity)).thenReturn(previous.principals());
        when(principalIds.load(identity)).thenReturn(previous.principalIdSets());
        Map<Long, List<Resource>> refreshedResources = new LinkedHashMap<>();
        for (Principal principal : previous.principals()) {
            refreshedResources.put(principal.principalId(), List.copyOf(previous.resources(principal.principalId())));
        }
        when(resources.load(previous.principals())).thenReturn(Map.copyOf(refreshedResources));
        Map<Long, List<ResourceIdSet>> refreshedResourceIds = new LinkedHashMap<>();
        for (Principal principal : previous.principals()) {
            refreshedResourceIds.put(
                    principal.principalId(),
                    List.copyOf(previous.resourceIdSets(principal.principalId())));
        }
        when(resourceIds.load(previous.principals())).thenReturn(Map.copyOf(refreshedResourceIds));
        SessionRefreshService refresh = new SessionRefreshService(
                identities,
                principals,
                principalIds,
                resources,
                resourceIds);

        Session result = refresh.refresh(previous);

        assertThat(result.id()).isEqualTo(previous.id());
        assertThat(result.authentications()).isEqualTo(previous.authentications());
        assertThat(result.issuedAt()).isEqualTo(previous.issuedAt());
        assertThat(result.expiresAt()).isEqualTo(previous.expiresAt());
        assertThat(result.account()).isSameAs(identity.account());
        assertThat(result.principals()).containsExactlyElementsOf(previous.principals());
        assertThat(result.principalIdSets()).singleElement()
                .satisfies(
                        principalIdSet -> assertThat(principalIdSet.ids().toArray())
                                .containsExactly(1_927_273_629L, 1_927_273_630L));
        assertThat(result.resourceIdSets(result.principals().getFirst().principalId()))
                .singleElement()
                .satisfies(
                        resourceIdSet -> assertThat(resourceIdSet.ids().toArray())
                                .containsExactly(1_927_273_629L, 1_927_273_630L));
    }
}
