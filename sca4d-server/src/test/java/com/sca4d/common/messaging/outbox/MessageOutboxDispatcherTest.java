package com.sca4d.common.messaging.outbox;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import org.junit.jupiter.api.Test;

/** 验证定时调度器按批次调用 Outbox 发送服务。 */
class MessageOutboxDispatcherTest {

    @Test
    void shouldDelegateScheduledDispatchToDeliveryService() {
        MessageOutboxDeliveryService deliveryService = mock(MessageOutboxDeliveryService.class);

        new MessageOutboxDispatcher(deliveryService).dispatch();

        verify(deliveryService).dispatchDueMessages();
    }
}
