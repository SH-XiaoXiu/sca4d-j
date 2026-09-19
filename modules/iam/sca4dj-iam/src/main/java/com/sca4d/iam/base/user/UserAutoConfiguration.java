package com.sca4d.iam.base.user;

import com.sca4d.iam.config.IamAutoConfiguration;
import org.jetbrains.annotations.NotNull;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.util.List;

/** 自动装配单一 Platform 身份系统使用的当前 User 参数解析器。 */
@AutoConfiguration(after = IamAutoConfiguration.class)
public class UserAutoConfiguration {
    /** 创建 User 层自动配置。 */
    public UserAutoConfiguration() {
    }

    /**
     * 注册当前 User 参数解析器。
     *
     * @return 当前 User 参数解析器
     */
    @Bean
    @ConditionalOnMissingBean(CurrentUserArgumentResolver.class)
    @NotNull
    CurrentUserArgumentResolver currentUserArgumentResolver() {
        return new CurrentUserArgumentResolver();
    }

    /**
     * 把当前 User 参数解析器安装到 Spring MVC。
     *
     * @param resolver 当前 User 参数解析器
     * @return MVC 参数解析器配置
     */
    @Bean
    @ConditionalOnBean(CurrentUserArgumentResolver.class)
    @NotNull
    WebMvcConfigurer userWebMvcConfigurer(@NotNull CurrentUserArgumentResolver resolver) {
        return new WebMvcConfigurer() {
            /** {@inheritDoc} */
            @Override
            public void addArgumentResolvers(
                    @NotNull List<@NotNull HandlerMethodArgumentResolver> resolvers) {
                resolvers.add(resolver);
            }
        };
    }
}
