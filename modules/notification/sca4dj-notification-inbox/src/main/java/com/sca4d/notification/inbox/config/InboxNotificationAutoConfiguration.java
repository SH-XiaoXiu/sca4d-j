package com.sca4d.notification.inbox.config;

import com.sca4d.core.api.id.IdGenerator;
import com.sca4d.core.api.notification.NotificationDelivery;
import com.sca4d.core.api.webrealtime.WebRealtimePublisher;
import com.sca4d.notification.NotificationDeliveryRouter;
import com.sca4d.notification.NotificationRequestedConsumer;
import com.sca4d.notification.config.NotificationAutoConfiguration;
import com.sca4d.notification.inbox.InboxNotificationDelivery;
import com.sca4d.notification.inbox.InboxRepository;
import com.sca4d.notification.inbox.InboxService;
import com.sca4d.notification.inbox.mapper.InboxMessageMapper;
import com.sca4d.notification.template.NotificationRichTextRenderer;
import com.sca4d.notification.template.NotificationTemplateRepository;
import org.jetbrains.annotations.NotNull;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.context.annotation.Bean;

import java.time.Clock;

/** 自动装配站内信持久化、查询、Realtime 提示和独立消息 Consumer。 */
@AutoConfiguration(after = NotificationAutoConfiguration.class)
@MapperScan(basePackageClasses = InboxMessageMapper.class)
public class InboxNotificationAutoConfiguration {

    /** 创建站内信自动配置。 */
    public InboxNotificationAutoConfiguration() {
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
            @NotNull NotificationTemplateRepository templates,
            @NotNull NotificationRichTextRenderer renderer,
            @NotNull InboxRepository repository,
            @NotNull InboxService service,
            @NotNull IdGenerator ids) {
        return new InboxNotificationDelivery(templates, renderer, repository, service, ids);
    }

    @Bean
    NotificationRequestedConsumer inboxNotificationRequestedConsumer(
            @NotNull NotificationDelivery inboxNotificationDelivery,
            @NotNull NotificationDeliveryRouter deliveries) {
        return new NotificationRequestedConsumer(inboxNotificationDelivery, deliveries);
    }
}
