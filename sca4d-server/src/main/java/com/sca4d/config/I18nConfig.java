package com.sca4d.config;

import com.sca4d.common.i18n.LocalizedMessages;
import com.sca4d.common.i18n.SupportedLocale;
import org.jetbrains.annotations.NotNull;
import org.springframework.context.MessageSource;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.LocaleResolver;
import org.springframework.web.servlet.i18n.AcceptHeaderLocaleResolver;

/** 后端支持语言、请求语言解析和消息资源访问配置。 */
@Configuration
public class I18nConfig {

    /**
     * 注册只接受产品支持语言的 Accept-Language 解析器。
     *
     * @return 默认简体中文、支持简体中文和美国英语的请求语言解析器
     */
    @Bean
    public @NotNull LocaleResolver localeResolver() {
        AcceptHeaderLocaleResolver resolver = new AcceptHeaderLocaleResolver();
        resolver.setDefaultLocale(SupportedLocale.ZH_CN.locale());
        resolver.setSupportedLocales(SupportedLocale.locales());
        return resolver;
    }

    /**
     * 注册后端业务含义的共用本地化入口。
     *
     * @param messageSource Spring Boot 自动加载的项目消息资源
     * @return 按当前 Locale 解析消息键的入口
     */
    @Bean
    public @NotNull LocalizedMessages localizedMessages(@NotNull MessageSource messageSource) {
        return new LocalizedMessages(messageSource);
    }
}
