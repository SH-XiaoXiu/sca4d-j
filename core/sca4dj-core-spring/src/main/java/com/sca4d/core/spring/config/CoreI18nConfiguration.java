package com.sca4d.core.spring.config;

import com.sca4d.core.api.i18n.LocaleCatalog;
import com.sca4d.core.api.i18n.MessageCatalog;
import com.sca4d.core.api.i18n.TextLocalizer;
import com.sca4d.core.spring.i18n.CatalogMessageSource;
import com.sca4d.core.spring.i18n.ClasspathMessageCatalog;
import com.sca4d.core.spring.i18n.I18nProperties;
import com.sca4d.core.spring.i18n.RequestLocaleResolver;
import jakarta.validation.Validator;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.MessageSource;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Role;
import org.springframework.context.support.AbstractApplicationContext;
import org.springframework.core.io.support.ResourcePatternResolver;
import org.springframework.validation.beanvalidation.LocalValidatorFactoryBean;
import org.springframework.web.servlet.DispatcherServlet;
import org.springframework.web.servlet.LocaleResolver;

import java.util.List;
import java.util.Locale;

/** 注册静态消息目录、请求语言解析和 Bean Validation 消息源。 */
@Role(BeanDefinition.ROLE_INFRASTRUCTURE)
class CoreI18nConfiguration {

    /** @param properties I18n 应用配置 @return 已校验的支持和默认 Locale 目录 */
    @Bean
    @ConditionalOnMissingBean
    LocaleCatalog localeCatalog(I18nProperties properties) {
        List<Locale> supported = properties.getSupportedLocales()
                .stream()
                .map(Locale::forLanguageTag)
                .toList();
        return new LocaleCatalog(supported, Locale.forLanguageTag(properties.getDefaultLocale()));
    }

    /** @param resources 类路径资源解析器 @param locales Locale 目录 @return 模块静态消息目录 */
    @Bean
    @ConditionalOnMissingBean
    MessageCatalog messageCatalog(ResourcePatternResolver resources, LocaleCatalog locales) {
        return new ClasspathMessageCatalog(resources, locales);
    }

    /** @param locales Locale 目录 @param messages 静态消息目录 @return 按 Locale 查找消息文本的文本本地化入口 */
    @Bean
    @ConditionalOnMissingBean
    TextLocalizer textLocalizer(LocaleCatalog locales, MessageCatalog messages) {
        return new TextLocalizer(locales, messages);
    }

    /** @param messages 静态消息目录 @param locales 静态消息支持和默认 Locale @return Spring 和 Bean Validation 使用的消息源 */
    @Bean(name = AbstractApplicationContext.MESSAGE_SOURCE_BEAN_NAME)
    @ConditionalOnMissingBean(name = AbstractApplicationContext.MESSAGE_SOURCE_BEAN_NAME)
    MessageSource messageSource(MessageCatalog messages, LocaleCatalog locales) {
        return new CatalogMessageSource(messages, locales);
    }

    /** @param locales Locale 目录 @return 保留动态数据请求语言的 HTTP Locale 解析器 */
    @Bean(name = DispatcherServlet.LOCALE_RESOLVER_BEAN_NAME)
    @ConditionalOnMissingBean(name = DispatcherServlet.LOCALE_RESOLVER_BEAN_NAME)
    LocaleResolver localeResolver(LocaleCatalog locales) {
        return new RequestLocaleResolver(locales);
    }

    /** @param messageSource 共享静态消息源 @return 使用共享消息目录插值的 Bean Validation 入口 */
    @Bean
    @ConditionalOnClass(name = "org.hibernate.validator.HibernateValidator")
    @ConditionalOnMissingBean(Validator.class)
    LocalValidatorFactoryBean validator(MessageSource messageSource) {
        LocalValidatorFactoryBean validator = new LocalValidatorFactoryBean();
        validator.setValidationMessageSource(messageSource);
        return validator;
    }
}
