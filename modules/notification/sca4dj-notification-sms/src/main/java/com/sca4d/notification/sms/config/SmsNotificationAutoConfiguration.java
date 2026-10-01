package com.sca4d.notification.sms.config;

import com.sca4d.core.api.concurrent.VersionConditions;
import com.sca4d.core.api.id.IdGenerator;
import com.sca4d.core.api.messaging.MessageBus;
import com.sca4d.core.api.notification.NotificationDelivery;
import com.sca4d.core.api.region.RegionCatalog;
import com.sca4d.core.spring.json.Sca4djJson;
import com.sca4d.core.spring.json.Sca4djMapper;
import com.sca4d.notification.NotificationDeliveryRouter;
import com.sca4d.notification.NotificationMessageCodec;
import com.sca4d.notification.NotificationMessageHandler;
import com.sca4d.notification.NotificationTypeCatalog;
import com.sca4d.notification.config.NotificationAutoConfiguration;
import com.sca4d.notification.sms.PrioritySmsProviderSelector;
import com.sca4d.notification.sms.SmsConfigurationService;
import com.sca4d.notification.sms.SmsDeliveryCheckCodec;
import com.sca4d.notification.sms.SmsDeliveryCheckHandler;
import com.sca4d.notification.sms.SmsDeliveryStore;
import com.sca4d.notification.sms.SmsNotificationDelivery;
import com.sca4d.notification.sms.SmsProviderClient;
import com.sca4d.notification.sms.SmsProviderSelector;
import com.sca4d.notification.sms.SmsRecipientResolver;
import com.sca4d.notification.sms.SmsRepository;
import com.sca4d.notification.sms.SmsRouteResolver;
import com.sca4d.notification.sms.mapper.SmsDeliveryMapper;
import com.sca4d.notification.sms.mapper.SmsProviderMapper;
import org.jetbrains.annotations.NotNull;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.transaction.PlatformTransactionManager;

import java.time.Clock;
import java.util.List;

/** 自动装配 SMS Provider、默认优先级选择、投递状态和独立通知 Consumer。 */
@AutoConfiguration(after = NotificationAutoConfiguration.class)
@MapperScan(basePackageClasses = {SmsProviderMapper.class, SmsDeliveryMapper.class})
public class SmsNotificationAutoConfiguration {

    /** 创建 SMS 自动配置。 */
    public SmsNotificationAutoConfiguration() {
    }

    @Bean
    SmsRepository smsRepository(
            @NotNull SmsProviderMapper mapper,
            @Sca4djJson @NotNull Sca4djMapper objectMapper,
            @NotNull VersionConditions versionConditions) {
        return new SmsRepository(mapper, objectMapper, versionConditions);
    }

    @Bean
    SmsConfigurationService smsConfigurationService(
            @NotNull SmsRepository repository,
            @NotNull NotificationTypeCatalog notificationTypes,
            @NotNull List<@NotNull SmsProviderClient> transports,
            @NotNull IdGenerator ids) {
        return new SmsConfigurationService(repository, notificationTypes, transports, ids);
    }

    @Bean
    SmsDeliveryStore smsDeliveryStore(@NotNull SmsDeliveryMapper mapper, @NotNull IdGenerator ids) {
        return new SmsDeliveryStore(mapper, ids);
    }

    @Bean
    @ConditionalOnMissingBean
    SmsProviderSelector smsProviderSelector(@NotNull SmsRepository repository) {
        return new PrioritySmsProviderSelector(repository);
    }

    @Bean
    SmsNotificationDelivery smsNotificationDelivery(
            @NotNull SmsRecipientResolver recipients,
            @NotNull SmsRouteResolver routes,
            @NotNull SmsProviderSelector selector,
            @NotNull SmsRepository repository,
            @NotNull SmsDeliveryStore deliveries,
            @NotNull List<@NotNull SmsProviderClient> transports,
            @NotNull Clock clock,
            @NotNull MessageBus messages,
            @NotNull PlatformTransactionManager transactionManager) {
        return new SmsNotificationDelivery(
                recipients,
                RegionCatalog.standard(),
                routes,
                selector,
                repository,
                deliveries,
                transports,
                clock,
                messages,
                transactionManager);
    }

    @Bean
    NotificationMessageHandler smsNotificationMessageHandler(
            @NotNull NotificationDelivery smsNotificationDelivery,
            @NotNull NotificationDeliveryRouter deliveries,
            @NotNull NotificationMessageCodec codec) {
        return new NotificationMessageHandler(smsNotificationDelivery, deliveries, codec);
    }

    @Bean
    SmsDeliveryCheckCodec smsDeliveryCheckCodec(@Sca4djJson @NotNull Sca4djMapper objectMapper) {
        return new SmsDeliveryCheckCodec(objectMapper);
    }

    @Bean
    SmsDeliveryCheckHandler smsDeliveryCheckHandler(
            @NotNull SmsNotificationDelivery delivery,
            @NotNull SmsDeliveryCheckCodec codec) {
        return new SmsDeliveryCheckHandler(delivery, codec);
    }
}
