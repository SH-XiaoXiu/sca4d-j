package com.sca4d.config;

import com.sca4d.config.properties.Sca4djMailProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/** 注册动态 SMTP 连接的超时配置。 */
@Configuration
@EnableConfigurationProperties(Sca4djMailProperties.class)
public class MailConfig {
}
