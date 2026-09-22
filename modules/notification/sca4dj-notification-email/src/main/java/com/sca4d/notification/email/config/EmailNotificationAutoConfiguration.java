package com.sca4d.notification.email.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sca4d.core.api.concurrent.VersionConditions;
import com.sca4d.core.api.id.IdGenerator;
import com.sca4d.core.api.notification.NotificationDelivery;
import com.sca4d.core.api.security.SecretKeyring;
import com.sca4d.notification.NotificationDeliveryRouter;
import com.sca4d.notification.NotificationMessageCodec;
import com.sca4d.notification.NotificationMessageHandler;
import com.sca4d.notification.config.NotificationAutoConfiguration;
import com.sca4d.notification.email.EmailMessageService;
import com.sca4d.notification.email.EmailNotificationDelivery;
import com.sca4d.notification.email.EmailRecipientResolver;
import com.sca4d.notification.email.EmailRenderer;
import com.sca4d.notification.email.EmailTemplateRepository;
import com.sca4d.notification.email.mapper.EmailTemplateMapper;
import com.sca4d.notification.email.sender.EmailGateway;
import com.sca4d.notification.email.sender.EmailSenderRepository;
import com.sca4d.notification.email.sender.EmailSenderService;
import com.sca4d.notification.email.sender.SmtpEmailGateway;
import com.sca4d.notification.email.sender.mapper.EmailSenderMapper;
import org.jetbrains.annotations.NotNull;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;

/** 自动装配 Email 模板、SMTP 配置管理和通知投递实现。 */
@AutoConfiguration(after = NotificationAutoConfiguration.class)
@MapperScan(basePackageClasses = {EmailSenderMapper.class, EmailTemplateMapper.class})
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

    /** @return Email 模板持久化语义 */
    @Bean
    EmailTemplateRepository emailTemplateRepository(
            @NotNull EmailTemplateMapper mapper,
            @NotNull VersionConditions versionConditions,
            @NotNull ObjectMapper objectMapper) {
        return new EmailTemplateRepository(mapper, versionConditions, objectMapper);
    }

    /** @return Email 模板检查与渲染入口 */
    @Bean
    EmailRenderer emailRenderer() {
        return new EmailRenderer();
    }

    /** @return 使用 JavaMail 和 SecretKeyring 的默认 SMTP 操作 */
    @Bean
    @ConditionalOnMissingBean
    EmailGateway emailTransport(
            @NotNull SecretKeyring keyring,
            @NotNull EmailNotificationProperties properties) {
        return new SmtpEmailGateway(keyring, properties);
    }

    /** @return SMTP 配置管理服务 */
    @Bean
    EmailSenderService emailSenderService(
            @NotNull EmailSenderRepository senders,
            @NotNull EmailGateway transport,
            @NotNull SecretKeyring keyring,
            @NotNull IdGenerator ids) {
        return new EmailSenderService(senders, transport, keyring, ids);
    }

    /** @return 供业务 Job 使用的带附件 Email 同步发送入口 */
    @Bean
    EmailMessageService emailMessageService(
            @NotNull EmailSenderRepository senders,
            @NotNull EmailTemplateRepository templates,
            @NotNull EmailRenderer renderer,
            @NotNull EmailGateway transport,
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
            @NotNull EmailTemplateRepository templates,
            @NotNull EmailRenderer renderer,
            @NotNull EmailGateway transport) {
        return new EmailNotificationDelivery(recipients, senders, templates, renderer, transport);
    }

    /** @return Email 渠道独立确认和进入独立 DLQ 的通知 Consumer */
    @Bean
    NotificationMessageHandler emailNotificationMessageHandler(
            @NotNull NotificationDelivery emailNotificationDelivery,
            @NotNull NotificationDeliveryRouter deliveries,
            @NotNull NotificationMessageCodec codec) {
        return new NotificationMessageHandler(emailNotificationDelivery, deliveries, codec);
    }
}
