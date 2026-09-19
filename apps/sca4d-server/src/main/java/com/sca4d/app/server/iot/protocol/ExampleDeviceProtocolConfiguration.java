package com.sca4d.app.server.iot.protocol;

import com.sca4d.iot.emqx.protocol.MqttDeviceProtocol;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** 安装 Sca4d Server 示例使用的规范 JSON 设备协议和旧式二进制设备协议。 */
@Configuration(proxyBeanMethods = false)
public class ExampleDeviceProtocolConfiguration {

    /** 创建示例设备协议配置。 */
    public ExampleDeviceProtocolConfiguration() {
    }

    /** @return 字段稳定、上下行对称的 JSON 示例协议 */
    @Bean
    MqttDeviceProtocol standardClimateDeviceProtocol() {
        return StandardClimateDeviceProtocol.protocol();
    }

    /** @return 缺字段、二进制 TLV 和不对称下行帧组成的旧设备协议 */
    @Bean
    MqttDeviceProtocol legacyFreezerDeviceProtocol() {
        return LegacyFreezerDeviceProtocol.protocol();
    }
}
