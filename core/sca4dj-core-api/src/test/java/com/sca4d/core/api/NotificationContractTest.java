package com.sca4d.core.api;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.sca4d.core.api.notification.Notification;
import com.sca4d.core.api.notification.NotificationChannel;
import com.sca4d.core.api.notification.NotificationCode;
import com.sca4d.core.api.notification.NotificationRecipient;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/** 验证渠道无关通知值对编码、接收人、语言和变量的稳定限制。 */
class NotificationContractTest {

    @Test
    void createsAccountNotificationWithoutChannelAddress() {
        Notification notification = new Notification(
                new NotificationCode("authentication.email-code"),
                NotificationRecipient.account(42),
                Locale.SIMPLIFIED_CHINESE,
                Map.of("code", "123456"),
                null,
                Set.of(new NotificationChannel("TEST/EMAIL")));

        assertEquals("42", notification.recipient().id());
        assertEquals("zh-CN", notification.locale().toLanguageTag());
        assertEquals(Set.of("code"), notification.variables().keySet());
    }

    @Test
    void rejectsInvalidCodesRecipientsLocalesAndVariableNames() {
        assertThrows(IllegalArgumentException.class, () -> new NotificationCode("notification/*"));
        assertThrows(IllegalArgumentException.class, () -> NotificationRecipient.account(0));
        assertThrows(
                IllegalArgumentException.class,
                () -> new Notification(
                        new NotificationCode("valid"),
                        NotificationRecipient.account(1),
                        Locale.ROOT,
                        Map.of(),
                        null,
                        Set.of(new NotificationChannel("TEST/EMAIL"))));
        assertThrows(
                IllegalArgumentException.class,
                () -> new Notification(
                        new NotificationCode("valid"),
                        NotificationRecipient.account(1),
                        Locale.ENGLISH,
                        Map.of("not-valid", "value"),
                        null,
                        Set.of(new NotificationChannel("TEST/EMAIL"))));
    }

    @Test
    void rejectsExcessiveVariableCountAndValueLength() {
        LinkedHashMap<String, String> excessiveVariables = new LinkedHashMap<>();
        for (int index = 0; index <= 100; index++) {
            excessiveVariables.put("value" + index, "x");
        }

        assertThrows(
                IllegalArgumentException.class,
                () -> new Notification(
                        new NotificationCode("valid"),
                        NotificationRecipient.account(1),
                        Locale.ENGLISH,
                        excessiveVariables,
                        null,
                        Set.of(new NotificationChannel("TEST/EMAIL"))));
        assertThrows(
                IllegalArgumentException.class,
                () -> new Notification(
                        new NotificationCode("valid"),
                        NotificationRecipient.account(1),
                        Locale.ENGLISH,
                        Map.of("value", "x".repeat(4097)),
                        null,
                        Set.of(new NotificationChannel("TEST/EMAIL"))));

        assertThrows(
                IllegalArgumentException.class,
                () -> new Notification(
                        new NotificationCode("valid"),
                        NotificationRecipient.account(1),
                        Locale.ENGLISH,
                        Map.of(),
                        null,
                        Set.of()));
    }
}
