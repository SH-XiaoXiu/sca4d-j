package com.sca4d.gateway;

import org.jetbrains.annotations.NotNull;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

/** 启动 Sca4d-J 独立负载均衡 Gateway。 */
@EnableScheduling
@SpringBootApplication
public class Sca4dGatewayApplication {

    /** 禁止创建 Gateway 启动入口实例。 */
    private Sca4dGatewayApplication() {
    }

    /**
     * 启动 Gateway 并加载租约注册、健康检查和路由配置。
     *
     * @param arguments Spring Boot 命令行参数
     */
    public static void main(String @NotNull [] arguments) {
        SpringApplication.run(Sca4dGatewayApplication.class, arguments);
    }
}
