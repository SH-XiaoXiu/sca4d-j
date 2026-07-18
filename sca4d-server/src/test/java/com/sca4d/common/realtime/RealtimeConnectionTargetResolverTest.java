package com.sca4d.common.realtime;

import static org.assertj.core.api.Assertions.assertThat;

import com.sca4d.common.auth.Principal;
import com.sca4d.common.auth.PrincipalType;
import com.sca4d.common.auth.Subject;
import org.junit.jupiter.api.Test;

/** 验证 Subject 和 Principal 如何转换为实时连接目标。 */
class RealtimeConnectionTargetResolverTest {

    @Test
    void shouldResolveTargetsFromCurrentAuthOnly() {
        Subject subject = new Subject(7L, "Alice");
        Principal principal = Principal.of(11L, PrincipalType.DEFAULT, 7L, 100L);
        DefaultRealtimeConnectionTargetResolver resolver = new DefaultRealtimeConnectionTargetResolver();

        assertThat(resolver.resolve(subject, principal)).extracting(RealtimeTarget::routingKey)
                .containsExactly("subject.7", "topic.client-state", "principal.default.11", "tenant.100");
    }

}
