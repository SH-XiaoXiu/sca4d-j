package com.sca4d.common.permission;

import static org.assertj.core.api.Assertions.assertThat;

import com.sca4d.common.i18n.LocalizedMessages;
import com.sca4d.common.i18n.LocalizedMessagesFixture;
import com.sca4d.common.i18n.SupportedLocale;
import com.sca4d.common.realtime.RealtimePermissions;
import com.sca4d.im.ImPermissions;
import com.sca4d.notification.NotificationPermissions;
import com.sca4d.platform.PlatformPermissions;
import com.sca4d.system.SystemPermissions;
import com.sca4d.tenant.TenantPermissions;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** 验证每个权限编码都有完整的中英文名称和说明。 */
class PermissionMessageContractTest {

    @Test
    void shouldProvideEveryPermissionNameInAllSupportedLanguages() {
        PermissionCatalog catalog = new PermissionCatalog(List.of(
                new PlatformPermissions(),
                new TenantPermissions(),
                new SystemPermissions(),
                new RealtimePermissions(),
                new ImPermissions(),
                new NotificationPermissions()));
        LocalizedMessages messages = LocalizedMessagesFixture.create();

        assertThat(catalog.items()).extracting(PermissionItem::getNameKey).doesNotHaveDuplicates();
        List<Locale> locales = new ArrayList<>();
        locales.add(Locale.ROOT);
        locales.addAll(SupportedLocale.locales());
        for (Locale locale : locales) {
            catalog.items().forEach(permission -> assertThat(messages.get(locale, permission.getNameKey())).isNotBlank());
        }
    }
}
