package com.sca4d.app.server.notification;

import com.sca4d.core.api.pipeline.Pipeline;
import com.sca4d.core.api.region.Region;
import com.sca4d.core.api.setting.LogicSetting;
import com.sca4d.core.api.setting.SettingCodec;
import com.sca4d.core.api.setting.SettingOwner;
import com.sca4d.core.api.setting.Settings;
import com.sca4d.notification.NotificationLocaleResolver;
import com.sca4d.notification.NotificationRegionResolver;
import com.sca4d.notification.sms.SmsRoute;
import com.sca4d.notification.sms.SmsRouteResolver;
import org.jetbrains.annotations.NotNull;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.Locale;

/** 用 Account LogicSetting 为 Notification 框架解析全局语言和业务区域。 */
@Configuration(proxyBeanMethods = false)
public class PlatformNotificationConfiguration {
    private static final Region CHINA_MAINLAND = new Region("CN");

    /** 中国大陆手机号使用的 SMS Provider 路由。 */
    public static final @NotNull SmsRoute CHINA_MAINLAND_SMS = new SmsRoute("china-mainland");

    /** 非中国大陆手机号使用的 SMS Provider 路由。 */
    public static final @NotNull SmsRoute INTERNATIONAL_SMS = new SmsRoute("international");

    public static final @NotNull LogicSetting<String> LANGUAGE = new LogicSetting<>(
            "account.language",
            1,
            "zh-CN",
            SettingCodec.text(64),
            Pipeline.identity(String.class),
            false);

    public static final @NotNull LogicSetting<String> REGION = new LogicSetting<>(
            "account.region",
            1,
            "GLOBAL",
            SettingCodec.text(128),
            Pipeline.identity(String.class),
            false);

    @Bean
    LogicSetting<String> accountLanguageSetting() {
        return LANGUAGE;
    }

    @Bean
    LogicSetting<String> accountRegionSetting() {
        return REGION;
    }

    /** @return 从 Account 设置读取 BCP 47 全局语言的解析器 */
    @Bean
    NotificationLocaleResolver platformNotificationLocaleResolver(@NotNull Settings settings) {
        return recipient -> Locale.forLanguageTag(
                settings.get(
                        LANGUAGE,
                        new SettingOwner(recipient.type(), recipient.id())));
    }

    /** @return 从 Account 设置读取具体应用业务区域的解析器 */
    @Bean
    NotificationRegionResolver platformNotificationRegionResolver(@NotNull Settings settings) {
        return recipient -> java.util.Optional.of(
                new Region(
                        settings.get(
                                REGION,
                                new SettingOwner(recipient.type(), recipient.id()))));
    }

    /** @return 按类型化电话目的地区域选择此应用 SMS 路由的解析器 */
    @Bean
    SmsRouteResolver platformSmsRouteResolver() {
        return (code, region, destination, locale) -> CHINA_MAINLAND.equals(destination.region())
                ? CHINA_MAINLAND_SMS
                : INTERNATIONAL_SMS;
    }
}
