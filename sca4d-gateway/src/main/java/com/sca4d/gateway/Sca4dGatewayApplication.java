package com.sca4d.gateway;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * 脚手架可选网关启动类。
 */
@EnableScheduling
@SpringBootApplication
public class Sca4dGatewayApplication {

    /**
     * 启动可选 Gateway 进程。
     *
     * @param args Spring Boot 命令行参数
     */
    public static void main(String[] args) {
        SpringApplication.run(Sca4dGatewayApplication.class, args);
    }
}
