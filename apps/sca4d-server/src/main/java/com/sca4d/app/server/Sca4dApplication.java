package com.sca4d.app.server;

import com.sca4d.app.server.iot.AppIotProperties;
import com.sca4d.app.server.mapper.PlatformUserProfileMapper;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

/** 启动 Sca4d-J 完整示例后端应用。 */
@SpringBootApplication
@MapperScan(basePackageClasses = PlatformUserProfileMapper.class)
@EnableConfigurationProperties(AppIotProperties.class)
public class Sca4dApplication {

    /** 禁止创建启动入口实例。 */
    private Sca4dApplication() {
    }

    /**
     * 启动后端服务并加载 Core Spring 与业务模块的自动配置。
     *
     * @param arguments 应用启动参数
     */
    public static void main(String[] arguments) {
        SpringApplication.run(Sca4dApplication.class, arguments);
    }
}
