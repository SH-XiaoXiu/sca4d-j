package com.sca4d.controllers;

import static org.assertj.core.api.Assertions.assertThat;

import com.sca4d.common.exception.ResponseCode;
import com.sca4d.common.i18n.LocalizedMessagesFixture;
import com.sca4d.common.permission.PermissionCatalog;
import com.sca4d.common.permission.PermissionType;
import com.sca4d.common.realtime.RealtimePermissions;
import com.sca4d.im.ImPermissions;
import com.sca4d.notification.NotificationPermissions;
import com.sca4d.system.SystemPermissions;
import org.junit.jupiter.api.Test;
import org.springframework.context.i18n.LocaleContextHolder;

import java.util.List;
import java.util.Locale;

/** 验证权限目录接口返回树形和扁平列表。 */
class PermissionControllerTest {

    @Test
    void shouldReturnPermissionCatalogApiResponse() {
        PermissionController controller = new PermissionController(
                new PermissionCatalog(List.of(
                        new SystemPermissions(),
                        new RealtimePermissions(),
                        new ImPermissions(),
                        new NotificationPermissions())),
                LocalizedMessagesFixture.create());

        try {
            LocaleContextHolder.setLocale(Locale.SIMPLIFIED_CHINESE);
            var chinese = controller.catalog();
            LocaleContextHolder.setLocale(Locale.US);
            var english = controller.catalog();

            assertThat(chinese.getCode()).isEqualTo(ResponseCode.SUCCESS.getCode());
            assertThat(chinese.getData()).isNotNull();
            assertThat(chinese.getData().getTotal()).isEqualTo(chinese.getData().getItems().size());
            assertThat(chinese.getData().getTree())
                    .extracting("code")
                    .contains("system", "realtime", "im", "notification");
            assertThat(chinese.getData().getItems()).anySatisfy(item -> {
                assertThat(item.getCode()).isEqualTo(SystemPermissions.OPERATION_LOGS_READ);
                assertThat(item.getParentCode()).isEqualTo("system:operation-logs");
                assertThat(item.getNameKey()).isEqualTo("permission.system.operation-logs.read");
                assertThat(item.getName()).isEqualTo("查看操作日志");
                assertThat(item.getType()).isEqualTo(PermissionType.ACTION);
                assertThat(item.isGrantable()).isTrue();
            });
            assertThat(english.getData().getItems()).anySatisfy(item -> {
                assertThat(item.getCode()).isEqualTo(SystemPermissions.OPERATION_LOGS_READ);
                assertThat(item.getNameKey()).isEqualTo("permission.system.operation-logs.read");
                assertThat(item.getName()).isEqualTo("View operation logs");
            });
        } finally {
            LocaleContextHolder.resetLocaleContext();
        }
    }

}
