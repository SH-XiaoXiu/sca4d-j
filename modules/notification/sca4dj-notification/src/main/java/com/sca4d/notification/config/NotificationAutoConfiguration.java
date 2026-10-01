package com.sca4d.notification.config;

import com.fasterxml.jackson.databind.JsonNode;
import com.sca4d.core.api.i18n.LocaleCatalog;
import com.sca4d.core.api.id.IdGenerator;
import com.sca4d.core.api.messaging.MessageBus;
import com.sca4d.core.api.notification.NotificationDelivery;
import com.sca4d.core.api.notification.NotificationType;
import com.sca4d.core.api.notification.Notifications;
import com.sca4d.core.api.setting.access.SettingReader;
import com.sca4d.core.api.setting.access.SettingWriter;
import com.sca4d.core.api.setting.definition.LogicSetting;
import com.sca4d.core.api.setting.definition.SystemSetting;
import com.sca4d.core.spring.json.Sca4djJson;
import com.sca4d.core.spring.json.Sca4djMapper;
import com.sca4d.notification.DefaultNotifications;
import com.sca4d.notification.NotificationDeliveryRouter;
import com.sca4d.notification.NotificationLocaleResolver;
import com.sca4d.notification.NotificationMessageCodec;
import com.sca4d.notification.NotificationRegionResolver;
import com.sca4d.notification.NotificationTypeCatalog;
import org.jetbrains.annotations.NotNull;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;

import java.util.List;
import java.util.Optional;

/** 自动装配类型化通知、Settings 包装和渠道路由。 */
@AutoConfiguration
public class NotificationAutoConfiguration {

    /** 创建本应用上下文的模块设置，来源由 Spring 显式注入。 */
    @Bean
    NotificationSettings notificationSettings(SettingReader<String> reader, SettingWriter<String> writer) {
        return new NotificationSettings(reader, writer);
    }

    public NotificationAutoConfiguration() {
    }

    /** @return 管理员维护的通知渠道系统默认值定义 */
    @Bean
    SystemSetting<JsonNode> notificationChannelDefaultsSetting(NotificationSettings settings) {
        return settings.CHANNEL_DEFAULTS;
    }

    /** @return 接收人维护的通知渠道覆盖值定义 */
    @Bean
    LogicSetting<JsonNode> notificationChannelPreferencesSetting(NotificationSettings settings) {
        return settings.CHANNEL_PREFERENCES;
    }

    /** @return 校验重复编码并保存当前应用安装通知类型的目录 */
    @Bean
    NotificationTypeCatalog notificationTypeCatalog(
            @NotNull List<@NotNull NotificationType<?>> types) {
        return new NotificationTypeCatalog(types);
    }

    /** @return 未由具体应用提供语言设置时使用应用默认 Locale */
    @Bean
    @ConditionalOnMissingBean
    NotificationLocaleResolver notificationLocaleResolver(@NotNull LocaleCatalog locales) {
        return recipient -> locales.defaultLocale();
    }

    /** @return 未建立业务区域体系时返回空区域 */
    @Bean
    @ConditionalOnMissingBean
    NotificationRegionResolver notificationRegionResolver() {
        return recipient -> Optional.empty();
    }

    /** @return 同步选择并执行所有可用渠道的路由器 */
    @Bean
    NotificationDeliveryRouter notificationDeliveryRouter(
            @NotNull List<@NotNull NotificationDelivery> deliveries) {
        return new NotificationDeliveryRouter(deliveries);
    }

    /** @return NotificationMessage 与 RabbitMQ payload 之间的稳定转换 */
    @Bean
    NotificationMessageCodec notificationMessageCodec(@Sca4djJson @NotNull Sca4djMapper objectMapper) {
        return new NotificationMessageCodec(objectMapper);
    }

    /** @return 解析领域通知并支持同步或 Outbox 发布的公共入口 */
    @Bean
    Notifications notifications(
            @NotNull NotificationTypeCatalog types,
            @NotNull NotificationSettings settings,
            @NotNull NotificationLocaleResolver locales,
            @NotNull NotificationRegionResolver regions,
            @NotNull NotificationDeliveryRouter deliveries,
            @NotNull MessageBus messages,
            @NotNull IdGenerator ids) {
        return new DefaultNotifications(types, settings, locales, regions, deliveries, messages, ids);
    }
}
