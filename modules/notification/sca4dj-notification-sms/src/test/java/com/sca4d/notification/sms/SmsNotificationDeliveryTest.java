package com.sca4d.notification.sms;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.sca4d.core.api.concurrent.Version;
import com.sca4d.core.api.messaging.MessageBus;
import com.sca4d.core.api.id.Id;
import com.sca4d.core.api.name.code.TreeCode;
import com.sca4d.core.api.notification.Notification;
import com.sca4d.core.api.notification.NotificationRecipient;
import com.sca4d.core.api.region.ParsedPhoneNumber;
import com.sca4d.core.api.region.Region;
import com.sca4d.core.api.region.RegionCatalog;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.transaction.PlatformTransactionManager;

import java.time.Clock;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * 验证 SMS 公共投递器不内置国家分区，并向应用路由策略提供类型化事实。
 */
class SmsNotificationDeliveryTest {

    @Test
    @DisplayName("应用路由解析器决定任意路由，Transport 接收类型化电话目的地")
    void applicationResolverChoosesRoute() {
        SmsRecipientResolver recipients = mock(SmsRecipientResolver.class);
        SmsRouteResolver routes = mock(SmsRouteResolver.class);
        SmsProviderSelector selector = mock(SmsProviderSelector.class);
        SmsRepository repository = mock(SmsRepository.class);
        SmsDeliveryStore deliveries = mock(SmsDeliveryStore.class);
        SmsProviderClient transport = mock(SmsProviderClient.class);
        MessageBus messages = mock(MessageBus.class);
        SmsRoute route = new SmsRoute("east-asia-primary");
        SmsProvider provider = new SmsProvider(11, "aliyun-primary", "ALIYUN", "primary", 10, true, new Version(0));
        SmsProviderBinding binding = new SmsProviderBinding(
                12,
                provider.providerId(),
                TreeCode.of("AUTHENTICATION/SMS_CODE"),
                route,
                "zh-CN",
                "Sca4d",
                "SMS_1",
                Map.of("code", "code"),
                true,
                new Version(0));
        Notification notification = new Notification(
                binding.code(),
                NotificationRecipient.account(7),
                Locale.SIMPLIFIED_CHINESE,
                Map.of("code", "123456"),
                new Region("tenant-east"),
                Set.of(SmsChannels.SMS));

        when(recipients.resolve(notification.recipient())).thenReturn(Optional.of("+8613800138000"));
        when(routes.resolve(any(), any(), any(), any())).thenReturn(route);
        when(selector.candidates(eq(route))).thenReturn(List.of(provider));
        when(repository.binding(provider.providerId(), notification.code(), route, notification.locale()))
                .thenReturn(Optional.of(binding));
        when(deliveries.begin(any(), eq(provider), eq(route), any(), any())).thenReturn(true);
        when(transport.providerType()).thenReturn("ALIYUN");
        when(transport.send(eq(provider), eq(binding), any(), eq(Map.of("code", "123456"))))
                .thenReturn(new SmsProviderResult(SmsDeliveryStatus.DELIVERED, "request-1", "biz-1", null));

        SmsNotificationDelivery delivery = new SmsNotificationDelivery(
                recipients,
                RegionCatalog.standard(),
                routes,
                selector,
                repository,
                deliveries,
                List.of(transport),
                Clock.systemUTC(),
                messages,
                mock(PlatformTransactionManager.class));
        delivery.deliver(Id.of(21), notification);

        ArgumentCaptor<ParsedPhoneNumber> routeDestination = ArgumentCaptor.forClass(ParsedPhoneNumber.class);
        verify(routes).resolve(
                eq(notification.code()),
                eq(new Region("tenant-east")),
                routeDestination.capture(),
                eq(notification.locale()));
        assertThat(routeDestination.getValue().region()).isEqualTo(new Region("CN"));
        ArgumentCaptor<ParsedPhoneNumber> transportDestination = ArgumentCaptor.forClass(ParsedPhoneNumber.class);
        verify(transport).send(
                eq(provider),
                eq(binding),
                transportDestination.capture(),
                eq(Map.of("code", "123456")));
        assertThat(transportDestination.getValue().value()).isEqualTo("+8613800138000");
    }
}
