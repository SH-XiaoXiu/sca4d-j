package com.sca4d.common.realtime;

import static org.assertj.core.api.Assertions.assertThat;

import com.sca4d.common.auth.PrincipalType;
import org.junit.jupiter.api.Test;

/** 验证实时目标编码、routing key 和匹配规则。 */
class RealtimeTargetTest {

    @Test
    void shouldBuildStableRoutingKeysAndStompDestinations() {
        RealtimeTarget subject = RealtimeTarget.subject(7L);
        RealtimeTarget principal = RealtimeTarget.principal(PrincipalType.DEFAULT, 11L);
        RealtimeTarget topic = RealtimeTarget.topic("Notice Created");

        assertThat(subject.routingKey()).isEqualTo("subject.7");
        assertThat(principal.routingKey()).isEqualTo("principal.default.11");
        assertThat(topic.routingKey()).isEqualTo("topic.notice_created");
        assertThat(principal.stompDestination()).isEqualTo(RealtimeRoutes.realtimeTopic("principal.default.11"));
    }
}
