package com.sca4d.system;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.sca4d.common.realtime.RealtimePublishRequest;
import com.sca4d.common.realtime.RealtimePublisher;
import com.sca4d.system.realtime.SystemRealtimePublisher;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.ObjectProvider;

/** 验证菜单变化通过所有认证连接共同订阅的状态主题发布。 */
class SystemRealtimePublisherTest {

    @Test
    void shouldPublishMenuChangeWithoutMenuPayload() {
        @SuppressWarnings("unchecked")
        ObjectProvider<RealtimePublisher> provider = mock(ObjectProvider.class);
        RealtimePublisher publisher = mock(RealtimePublisher.class);
        when(provider.getIfAvailable()).thenReturn(publisher);

        new SystemRealtimePublisher(provider).menusChanged();

        ArgumentCaptor<RealtimePublishRequest> request = ArgumentCaptor.forClass(RealtimePublishRequest.class);
        verify(publisher).publish(request.capture());
        assertThat(request.getValue().getTarget().routingKey()).isEqualTo("topic.client-state");
        assertThat(request.getValue().getEvent()).isEqualTo("system.menus.changed");
        assertThat(request.getValue().getData()).isNull();
    }
}
