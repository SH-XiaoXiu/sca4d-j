package com.sca4d.notification.sms.aliyun.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sca4d.notification.sms.SmsTransport;
import com.sca4d.notification.sms.aliyun.AliyunSmsProperties;
import com.sca4d.notification.sms.aliyun.AliyunSmsTransport;
import com.sca4d.notification.sms.config.SmsNotificationAutoConfiguration;
import org.jetbrains.annotations.NotNull;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;

/** 自动装配阿里云 SMS V2 SDK Transport。 */
@AutoConfiguration(after = SmsNotificationAutoConfiguration.class)
@EnableConfigurationProperties(AliyunSmsProperties.class)
public class AliyunSmsAutoConfiguration {

    /** 创建阿里云 SMS 自动配置。 */
    public AliyunSmsAutoConfiguration() {
    }

    /** @return 使用部署凭据和数据库 Provider 配置的阿里云 Transport */
    @Bean
    SmsTransport aliyunSmsTransport(
            @NotNull AliyunSmsProperties properties,
            @NotNull ObjectMapper objectMapper) {
        return new AliyunSmsTransport(properties, objectMapper);
    }
}
