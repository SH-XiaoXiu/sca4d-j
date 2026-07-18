package com.sca4d;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/** 启动 SCA4D 后端服务并加载 Spring 配置。 */
@SpringBootApplication
public class Sca4dApplication {

    /**
     * 启动 SCA4D 服务进程。
     */
    public static void main(String[] args) {
        SpringApplication.run(Sca4dApplication.class, args);
    }

}
