package com.sca4d.im;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.sca4d.common.realtime.RealtimePublishRequest;
import com.sca4d.common.realtime.RealtimePublisher;
import com.sca4d.im.realtime.ImRealtimePublisher;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.ObjectProvider;

import java.util.Arrays;
import java.util.List;

/** 验证即时消息在事务提交后发送给目标会话成员。 */
class ImRealtimePublisherTest {

    @Test
    void shouldPublishOncePerUniqueSubject() {
        @SuppressWarnings("unchecked")
        ObjectProvider<RealtimePublisher> provider = mock(ObjectProvider.class);
        RealtimePublisher publisher = mock(RealtimePublisher.class);
        when(provider.getIfAvailable()).thenReturn(publisher);
        ImRealtimePublisher realtimePublisher = new ImRealtimePublisher(provider);
        List<Long> subjectIds = Arrays.asList(1L, 1L, 2L, null);

        realtimePublisher.conversationUpdated(subjectIds, 10L);

        ArgumentCaptor<RealtimePublishRequest> request = ArgumentCaptor.forClass(RealtimePublishRequest.class);
        verify(publisher, times(2)).publish(request.capture());
        assertThat(request.getAllValues())
                .extracting(item -> item.getTarget().getValue())
                .containsExactly("1", "2");
    }
}
