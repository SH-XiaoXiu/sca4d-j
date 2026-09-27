package com.sca4d.app.server.controllers;

import com.sca4d.app.server.notification.NotificationSettingsService;
import com.sca4d.app.server.notification.request.ReplaceNotificationSettingRequest;
import com.sca4d.app.server.notification.view.NotificationSettingsView;
import com.sca4d.app.server.platform.PlatformPrincipalSelection;
import com.sca4d.core.spring.web.ApiResponse;
import com.sca4d.core.spring.web.annotation.CurrentAccount;
import com.sca4d.core.spring.web.annotation.CurrentPrincipal;
import com.sca4d.core.spring.web.annotation.IfMatch;
import com.sca4d.notification.NotificationTypeCatalog;
import org.jetbrains.annotations.NotNull;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Objects;

/** 管理当前 Account 的领域化通知设置，不暴露通用 Settings key。 */
@RestController
@RequestMapping("/me/notification-settings")
@CurrentPrincipal(selection = PlatformPrincipalSelection.class)
public class NotificationSettingsController {
    private final NotificationSettingsService settings;

    private final NotificationTypeCatalog notificationTypes;

    /** 创建当前 Account 通知设置控制器。 */
    public NotificationSettingsController(
            @NotNull NotificationSettingsService settings,
            @NotNull NotificationTypeCatalog notificationTypes) {
        this.settings = Objects.requireNonNull(settings, "NotificationSettingsService 不能为空");
        this.notificationTypes = Objects.requireNonNull(notificationTypes, "NotificationTypeCatalog 不能为空");
    }

    /** @return 当前 Account 的通知语言、业务区域、可选渠道覆盖值和各自 Version */
    @GetMapping
    public @NotNull ApiResponse<NotificationSettingsView> inspect(@CurrentAccount @NotNull Long accountId) {
        return ApiResponse.ok(view(accountId));
    }

    /** 使用 `languageVersion` 对应的 `If-Match` 替换当前 Account 通知语言。 */
    @PutMapping("/language")
    @IfMatch
    public @NotNull ApiResponse<NotificationSettingsView> language(
            @CurrentAccount @NotNull Long accountId,
            @RequestBody @NotNull ReplaceNotificationSettingRequest request) {
        settings.replaceLanguage(accountId, request.getValue().asText());
        return ApiResponse.ok(view(accountId));
    }

    /** 使用 `regionVersion` 对应的 `If-Match` 替换当前 Account 业务区域。 */
    @PutMapping("/region")
    @IfMatch
    public @NotNull ApiResponse<NotificationSettingsView> region(
            @CurrentAccount @NotNull Long accountId,
            @RequestBody @NotNull ReplaceNotificationSettingRequest request) {
        settings.replaceRegion(accountId, request.getValue().asText());
        return ApiResponse.ok(view(accountId));
    }

    /** 使用 `channelsVersion` 对应的 `If-Match` 替换当前 Account 可选渠道覆盖值。 */
    @PutMapping("/channels")
    @IfMatch
    public @NotNull ApiResponse<NotificationSettingsView> channels(
            @CurrentAccount @NotNull Long accountId,
            @RequestBody @NotNull ReplaceNotificationSettingRequest request) {
        settings.replaceChannels(accountId, request.getValue());
        return ApiResponse.ok(view(accountId));
    }

    private @NotNull NotificationSettingsView view(long accountId) {
        return NotificationSettingsView.from(settings.inspect(accountId), notificationTypes.types());
    }
}
