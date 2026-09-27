package com.sca4d.notification.inbox.config;

import com.sca4d.core.api.concurrent.VersionConditions;
import com.sca4d.core.api.i18n.LocaleCatalog;
import com.sca4d.core.api.id.IdGenerator;
import com.sca4d.core.api.notification.NotificationDelivery;
import com.sca4d.core.api.webrealtime.WebRealtimePublisher;
import com.sca4d.core.spring.json.Sca4djJson;
import com.sca4d.core.spring.json.Sca4djMapper;
import com.sca4d.notification.NotificationDeliveryRouter;
import com.sca4d.notification.NotificationMessageCodec;
import com.sca4d.notification.NotificationMessageHandler;
import com.sca4d.notification.config.NotificationAutoConfiguration;
import com.sca4d.notification.inbox.InboxNotificationDelivery;
import com.sca4d.notification.inbox.InboxRepository;
import com.sca4d.notification.inbox.InboxService;
import com.sca4d.notification.inbox.mapper.InboxMessageMapper;
import com.sca4d.notification.inbox.template.InboxTemplateRenderer;
import com.sca4d.notification.inbox.template.InboxTemplateRepository;
import com.sca4d.notification.inbox.template.mapper.InboxTemplateMapper;
import org.jetbrains.annotations.NotNull;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.context.annotation.Bean;

import java.time.Clock;

/** 自动装配站内信模板、消息快照、Realtime 提示和独立消息 Handler。 */
@AutoConfiguration(after = NotificationAutoConfiguration.class)
@MapperScan(basePackageClasses = {InboxMessageMapper.class, InboxTemplateMapper.class})
public class InboxNotificationAutoConfiguration {

    /** 创建站内信自动配置。 */
    public InboxNotificationAutoConfiguration() {
    }

    /** @return 校验并渲染站内信 Tiptap 模板的组件 */
    @Bean
    InboxTemplateRenderer inboxTemplateRenderer(@NotNull LocaleCatalog locales) {
        return new InboxTemplateRenderer(locales);
    }

    /** @return 隐藏站内信模板 JSONB、Mapper 和乐观锁的持久化入口 */
    @Bean
    InboxTemplateRepository inboxTemplateRepository(
            @NotNull InboxTemplateMapper mapper,
            @NotNull VersionConditions versionConditions,
            @Sca4djJson @NotNull Sca4djMapper objectMapper) {
        return new InboxTemplateRepository(mapper, versionConditions, objectMapper);
    }

    @Bean
    InboxRepository inboxRepository(@NotNull InboxMessageMapper mapper) {
        return new InboxRepository(mapper);
    }

    @Bean
    InboxService inboxService(
            @NotNull InboxRepository repository,
            @NotNull WebRealtimePublisher realtime,
            @NotNull Clock clock) {
        return new InboxService(repository, realtime, clock);
    }

    @Bean
    NotificationDelivery inboxNotificationDelivery(
            @NotNull InboxTemplateRepository templates,
            @NotNull InboxTemplateRenderer renderers,
            @NotNull InboxRepository repository,
            @NotNull InboxService service,
            @NotNull IdGenerator ids) {
        return new InboxNotificationDelivery(templates, renderers, repository, service, ids);
    }

    @Bean
    NotificationMessageHandler inboxNotificationMessageHandler(
            @NotNull NotificationDelivery inboxNotificationDelivery,
            @NotNull NotificationDeliveryRouter deliveries,
            @NotNull NotificationMessageCodec codec) {
        return new NotificationMessageHandler(inboxNotificationDelivery, deliveries, codec);
    }
}
