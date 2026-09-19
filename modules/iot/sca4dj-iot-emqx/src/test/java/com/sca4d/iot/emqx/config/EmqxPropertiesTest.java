package com.sca4d.iot.emqx.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.sca4d.core.api.runtime.RuntimeNamespace;
import org.junit.jupiter.api.Test;

/** 验证 EMQX Server Client ID 的自动生成和显式覆盖。 */
class EmqxPropertiesTest {

    @Test
    void generatesDifferentClientIdsForIndependentInstances() {
        EmqxProperties first = new EmqxProperties();
        EmqxProperties second = new EmqxProperties();
        RuntimeNamespace namespace = new RuntimeNamespace("example-app");

        first.initializeClientId(namespace);
        second.initializeClientId(namespace);
        String firstClientId = first.getClientId();
        first.initializeClientId(namespace);

        assertThat(firstClientId).startsWith("example-app:sca4dj:iot:emqx:client:");
        assertThat(first.getClientId()).isEqualTo(firstClientId);
        assertThat(second.getClientId()).startsWith("example-app:sca4dj:iot:emqx:client:");
        assertThat(first.getClientId()).isNotEqualTo(second.getClientId());
    }

    @Test
    void keepsExplicitClientIdAndGeneratesForBlankOverride() {
        EmqxProperties properties = new EmqxProperties();

        properties.setClientId("sca4dj-server-1");
        properties.initializeClientId(new RuntimeNamespace("example-app"));
        assertThat(properties.getClientId()).isEqualTo("sca4dj-server-1");

        properties.setClientId(" ");
        properties.initializeClientId(new RuntimeNamespace("example-app"));
        assertThat(properties.getClientId()).startsWith("example-app:sca4dj:iot:emqx:client:");
    }

    @Test
    void derivesReceiveMaximumFromBoundedInboundCapacity() {
        EmqxProperties properties = new EmqxProperties();
        properties.setInboundWorkerCount(4);
        properties.setInboundQueueCapacity(64);

        assertThat(properties.inboundReceiveMaximum()).isEqualTo(64);
    }

    @Test
    void rejectsUnsafeInboundResourceConfigurationWhenEnabled() {
        EmqxProperties zeroQueue = enabledProperties();
        zeroQueue.setInboundQueueCapacity(0);
        assertThatThrownBy(zeroQueue::validate)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("inbound-queue-capacity");

        EmqxProperties tooManyWorkers = enabledProperties();
        tooManyWorkers.setInboundWorkerCount(33);
        assertThatThrownBy(tooManyWorkers::validate)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("inbound-worker-count");
    }

    /** 创建具有全部启用必填项的 EMQX 测试配置。 */
    private static EmqxProperties enabledProperties() {
        EmqxProperties properties = new EmqxProperties();
        properties.setEnabled(true);
        properties.setClientId("test-client");
        properties.setManagementBaseUrl("http://localhost:18083");
        properties.setManagementApiKey("test-key");
        properties.setManagementSecret("test-secret");
        return properties;
    }
}
