package com.sca4d.notification.email.config;

import com.sca4d.core.api.concurrent.VersionConditions;
import com.sca4d.core.api.id.IdGenerator;
import com.sca4d.core.api.notification.NotificationDelivery;
import com.sca4d.core.api.security.SecretKeyring;
import com.sca4d.notification.NotificationDeliveryRouter;
import com.sca4d.notification.NotificationRequestedConsumer;
import com.sca4d.notification.config.NotificationAutoConfiguration;
import com.sca4d.notification.email.EmailMessageService;
import com.sca4d.notification.email.EmailNotificationDelivery;
import com.sca4d.notification.email.EmailRecipientResolver;
import com.sca4d.notification.email.sender.EmailSenderRepository;
import com.sca4d.notification.email.sender.EmailSenderService;
import com.sca4d.notification.email.sender.EmailTransport;
import com.sca4d.notification.email.sender.SmtpEmailTransport;
import com.sca4d.notification.email.sender.mapper.EmailSenderMapper;
import com.sca4d.notification.template.NotificationRichTextRenderer;
import com.sca4d.notification.template.NotificationTemplateRepository;
import org.jetbrains.annotations.NotNull;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;

/** 自动装配 Email 模板、SMTP 配置管理和通知投递实现。 */
@AutoConfiguration(after = NotificationAutoConfiguration.class)
@MapperScan(basePackageClasses = EmailSenderMapper.class)
@EnableConfigurationProperties(EmailNotificationProperties.class)
public class EmailNotificationAutoConfiguration {

    /** 创建 Email Notification 自动配置。 */
    public EmailNotificationAutoConfiguration() {
    }

    /** @return SMTP 配置持久化语义 */
    @Bean
    EmailSenderRepository emailSenderRepository(
            @NotNull EmailSenderMapper mapper,
            @NotNull VersionConditions versionConditions) {
        return new EmailSenderRepository(mapper, versionConditions);
    }

    /** @return 使用 JavaMail 和 SecretKeyring 的默认 SMTP 操作 */
    @Bean
    @ConditionalOnMissingBean
    EmailTransport emailTransport(
            @NotNull SecretKeyring keyring,
            @NotNull EmailNotificationProperties properties) {
        return new SmtpEmailTransport(keyring, properties);
    }

    /** @return SMTP 配置管理服务 */
    @Bean
    EmailSenderService emailSenderService(
            @NotNull EmailSenderRepository senders,
            @NotNull EmailTransport transport,
            @NotNull SecretKeyring keyring,
            @NotNull IdGenerator ids) {
        return new EmailSenderService(senders, transport, keyring, ids);
    }

    /** @return 供业务 Job 使用的带附件 Email 同步发送入口 */
    @Bean
    EmailMessageService emailMessageService(
            @NotNull EmailSenderRepository senders,
            @NotNull NotificationTemplateRepository templates,
            @NotNull NotificationRichTextRenderer renderer,
            @NotNull EmailTransport transport,
            @NotNull EmailNotificationProperties properties) {
        return new EmailMessageService(
                senders,
                templates,
                renderer,
                transport,
                properties.getMaxAttachmentBytes());
    }

    /** @return 可被 Core Notification 路由发现的 Email 投递实现 */
    @Bean
    NotificationDelivery emailNotificationDelivery(
            @NotNull EmailRecipientResolver recipients,
            @NotNull EmailSenderRepository senders,
            @NotNull NotificationTemplateRepository templates,
            @NotNull NotificationRichTextRenderer renderer,
            @NotNull EmailTransport transport) {
        return new EmailNotificationDelivery(recipients, senders, templates, renderer, transport);
    }

    /** @return Email 渠道独立确认和进入独立 DLQ 的通知 Consumer */
    @Bean
    NotificationRequestedConsumer emailNotificationRequestedConsumer(
            @NotNull NotificationDelivery emailNotificationDelivery,
            @NotNull NotificationDeliveryRouter deliveries) {
        return new NotificationRequestedConsumer(emailNotificationDelivery, deliveries);
    }
}
