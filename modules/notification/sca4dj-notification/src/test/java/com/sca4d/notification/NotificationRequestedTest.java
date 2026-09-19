package com.sca4d.notification;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sca4d.core.api.notification.Notification;
import com.sca4d.core.api.notification.NotificationChannel;
import com.sca4d.core.api.notification.NotificationCode;
import com.sca4d.core.api.notification.NotificationRecipient;
import com.sca4d.core.api.region.Region;
import org.junit.jupiter.api.Test;

import java.util.Locale;
import java.util.Map;

/** 验证异步通知事件只传输公共通知值并可稳定往返。 */
class NotificationRequestedTest {

    @Test
    void roundTripsThroughJackson() throws Exception {
        Notification source = new Notification(
                new NotificationCode("authentication.email-code"),
                NotificationRecipient.account(42),
                Locale.SIMPLIFIED_CHINESE,
                Map.of("code", "123456"),
                new Region("CN"),
                java.util.Set.of(NotificationChannel.EMAIL));
        ObjectMapper mapper = new ObjectMapper();

        NotificationRequested decoded = mapper.readValue(
                mapper.writeValueAsBytes(NotificationRequested.from(source)),
                NotificationRequested.class);

        Notification restored = decoded.toNotification();
        assertThat(restored.code()).isEqualTo(source.code());
        assertThat(restored.recipient().type()).isEqualTo(source.recipient().type());
        assertThat(restored.recipient().id()).isEqualTo("42");
        assertThat(restored.locale()).isEqualTo(Locale.SIMPLIFIED_CHINESE);
        assertThat(restored.variables()).containsEntry("code", "123456");
        assertThat(restored.region()).isEqualTo(new Region("CN"));
        assertThat(restored.channels()).containsExactly(NotificationChannel.EMAIL);
    }
}
