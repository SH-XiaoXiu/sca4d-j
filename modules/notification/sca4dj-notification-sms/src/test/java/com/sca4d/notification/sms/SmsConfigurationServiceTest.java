package com.sca4d.notification.sms;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.sca4d.core.api.concurrent.Version;
import com.sca4d.core.api.error.BusinessException;
import com.sca4d.core.api.id.IdGenerator;
import com.sca4d.core.api.notification.NotificationChannel;
import com.sca4d.core.api.notification.NotificationType;
import com.sca4d.notification.NotificationTypeCatalog;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Optional;

/** 验证 SMS Provider 与绑定管理不会绕过已安装 Transport 和通知变量声明。 */
class SmsConfigurationServiceTest {

    private static final NotificationType<TestNotice> TYPE = NotificationType.define(
            "test.sms",
            TestNotice.class,
            definition -> {
                definition.required(NotificationChannel.SMS);
                definition.variable("code", (notice, locale) -> notice.code);
            },
            (notice, execution) -> execution.sendToAccount(notice.accountId));

    @Test
    @DisplayName("Provider 类型必须对应已安装 Transport")
    void rejectsProviderWithoutTransport() {
        SmsConfigurationService service = new SmsConfigurationService(
                mock(SmsRepository.class),
                new NotificationTypeCatalog(List.of(TYPE)),
                List.of(transport("ALIYUN")),
                () -> 1L);

        assertThatThrownBy(() -> service.createProvider("primary", "UNKNOWN", "primary", 10))
                .isInstanceOfSatisfying(
                        BusinessException.class,
                        exception -> org.assertj.core.api.Assertions.assertThat(exception.errorCode())
                                .isEqualTo(SmsNotificationErrorCode.PROVIDER_INVALID));
    }

    @Test
    @DisplayName("绑定参数只能引用 NotificationType 声明的变量")
    void rejectsBindingVariableOutsideNotificationType() {
        SmsRepository repository = mock(SmsRepository.class);
        when(repository.provider(1L)).thenReturn(Optional.of(provider()));
        SmsConfigurationService service = new SmsConfigurationService(
                repository,
                new NotificationTypeCatalog(List.of(TYPE)),
                List.of(transport("ALIYUN")),
                idGenerator());

        assertThatThrownBy(
                () -> service.createBinding(
                        1L,
                        "test.sms",
                        "china-mainland",
                        "zh-CN",
                        "Sca4d",
                        "SMS_1",
                        Map.of("value", "missing")))
                .isInstanceOfSatisfying(
                        BusinessException.class,
                        exception -> org.assertj.core.api.Assertions.assertThat(exception.errorCode())
                                .isEqualTo(SmsNotificationErrorCode.BINDING_INVALID));
    }

    @Test
    @DisplayName("Provider 没有启用绑定时不能启用")
    void rejectsProviderEnableWithoutEnabledBinding() {
        SmsRepository repository = mock(SmsRepository.class);
        when(repository.provider(1L)).thenReturn(Optional.of(provider()));
        when(repository.bindings(1L)).thenReturn(List.of());
        SmsConfigurationService service = new SmsConfigurationService(
                repository,
                new NotificationTypeCatalog(List.of(TYPE)),
                List.of(transport("ALIYUN")),
                idGenerator());

        assertThatThrownBy(() -> service.setProviderEnabled(1L, true))
                .isInstanceOfSatisfying(
                        BusinessException.class,
                        exception -> org.assertj.core.api.Assertions.assertThat(exception.errorCode())
                                .isEqualTo(SmsNotificationErrorCode.PROVIDER_CONFLICT));
    }

    private static SmsProvider provider() {
        return new SmsProvider(1L, "primary", "ALIYUN", "primary", 10, false, new Version(0));
    }

    private static SmsTransport transport(String type) {
        SmsTransport transport = mock(SmsTransport.class);
        when(transport.providerType()).thenReturn(type);
        return transport;
    }

    private static IdGenerator idGenerator() {
        return () -> 2L;
    }

    private static final class TestNotice {
        private final long accountId;

        private final String code;

        private TestNotice(long accountId, String code) {
            this.accountId = accountId;
            this.code = code;
        }
    }
}
