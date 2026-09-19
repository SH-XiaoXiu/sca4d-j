package com.sca4d.app.server.authentication;

import com.sca4d.app.server.service.ActionAuthenticationService;
import org.jetbrains.annotations.NotNull;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/** 注册示例应用的 Controller 动作认证拦截器。 */
@Configuration(proxyBeanMethods = false)
public class ActionAuthenticationWebConfiguration {

    /** 在 Session 恢复后、业务主体选择前消费动作认证结果。 */
    @Bean
    WebMvcConfigurer actionAuthenticationWebMvcConfigurer(
            ActionAuthenticationService authentications) {
        return new WebMvcConfigurer() {
            @Override
            public void addInterceptors(@NotNull InterceptorRegistry registry) {
                registry.addInterceptor(new ActionAuthenticationInterceptor(authentications))
                        .order(Ordered.HIGHEST_PRECEDENCE + 150);
            }
        };
    }
}
