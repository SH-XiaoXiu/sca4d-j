package com.sca4d.app.server.authentication;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/** 装配具体应用的联邦登录浏览器路由配置。 */
@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(FederatedLoginProperties.class)
public class FederatedLoginConfiguration {

    /** 创建配置装配类。 */
    public FederatedLoginConfiguration() {
    }
}
