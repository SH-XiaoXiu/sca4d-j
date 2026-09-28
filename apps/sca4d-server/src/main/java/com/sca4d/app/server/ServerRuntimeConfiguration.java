package com.sca4d.app.server;

import com.sca4d.core.api.identity.SystemPrincipals;
import com.sca4d.core.api.name.code.TreeCode;
import com.sca4d.core.api.runtime.InstanceCapability;
import com.sca4d.core.spring.security.SecretKeyringProperties;
import com.sca4d.iot.api.IotCapabilities;
import org.springframework.boot.autoconfigure.flyway.FlywayConfigurationCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** 声明 Sca4d Server 的业务 API、IoT API 和设备接入能力及服务器路径。 */
@Configuration(proxyBeanMethods = false)
public class ServerRuntimeConfiguration {

    /** 普通业务 API 使用 MAIN 系统身份；更具体的 IoT 路径由 IOT:API 负责。 */
    @Bean
    InstanceCapability mainApiCapability() {
        return new InstanceCapability(TreeCode.ofColon("MAIN:API"), SystemPrincipals.MAIN, List.of("/"));
    }

    /** 管理端和外部应用的 IoT HTTP 路径共同要求 IOT:API。 */
    @Bean
    InstanceCapability iotApiCapability() {
        return new InstanceCapability(
                TreeCode.ofColon(IotCapabilities.API),
                IotCapabilities.SYSTEM_IDENTITY,
                List.of("/iot", "/openapi/iot"));
    }

    /** MQTT 接入和 EMQX 回调可以由独立实例启用，系统身份仍为 IOT。 */
    @Bean
    InstanceCapability iotTransportCapability() {
        return new InstanceCapability(
                TreeCode.ofColon(IotCapabilities.TRANSPORT),
                IotCapabilities.SYSTEM_IDENTITY,
                List.of());
    }

    /** 把 Spring 已解析的密钥环配置传给 Flyway Java migration，避免迁移直接读取进程环境。 */
    @Bean
    FlywayConfigurationCustomizer flywaySecretKeyringPlaceholders(SecretKeyringProperties properties) {
        return configuration -> {
            Map<String, String> placeholders = new LinkedHashMap<>(configuration.getPlaceholders());
            if (properties.getActiveKeyId() != null) {
                placeholders.put("sca4dj_keyring_active_key_id", properties.getActiveKeyId());
            }
            properties.getKeys().forEach((keyId, value) -> placeholders.put("sca4dj_keyring_" + keyId + "_key", value));
            configuration.placeholders(placeholders);
        };
    }
}
