package com.sca4d.authentication;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.sca4d.authentication.realtime.AuthenticationRealtimePublisher;
import com.sca4d.common.realtime.RealtimePublishRequest;
import com.sca4d.common.realtime.RealtimePublisher;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.ObjectProvider;

/** 验证认证 Session 变化只发送给受影响 Subject 的 SSE 连接。 */
class AuthenticationRealtimePublisherTest {

    @Test
    void shouldPublishSessionChangeToSubjectSseTarget() {
        @SuppressWarnings("unchecked")
        ObjectProvider<RealtimePublisher> provider = mock(ObjectProvider.class);
        RealtimePublisher publisher = mock(RealtimePublisher.class);
        when(provider.getIfAvailable()).thenReturn(publisher);

        new AuthenticationRealtimePublisher(provider).sessionChanged(7L);

        ArgumentCaptor<RealtimePublishRequest> request = ArgumentCaptor.forClass(RealtimePublishRequest.class);
        verify(publisher).publishNow(request.capture());
        assertThat(request.getValue().getTarget().routingKey()).isEqualTo("subject.7");
        assertThat(request.getValue().getEvent()).isEqualTo("authentication.session.changed");
        assertThat(request.getValue().getData()).isNull();
    }
}
