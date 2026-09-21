package com.sca4d.iot.emqx.transport;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.sca4d.iot.emqx.protocol.EncodedMqttMessage;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;

/** 验证 MQTT 发布入口不会在路由 Bean 装配期间创建运行时。 */
class EmqxMqttTransportTest {

    @Test
    @DisplayName("只在实际发布消息时取得 EMQX MQTT 运行时")
    void resolvesRuntimeOnlyWhenPublishing() {
        @SuppressWarnings("unchecked")
        ObjectProvider<EmqxMqttRuntime> runtimes = mock(ObjectProvider.class);
        EmqxMqttRuntime runtime = mock(EmqxMqttRuntime.class);
        when(runtimes.getObject()).thenReturn(runtime);
        EmqxMqttTransport publisher = new EmqxMqttTransport(runtimes);

        verify(runtimes, never()).getObject();

        EncodedMqttMessage message = new EncodedMqttMessage("vendor/device/down", new byte[] {0x01});
        publisher.publish(message);

        verify(runtimes).getObject();
        verify(runtime).publish(message);
    }
}
