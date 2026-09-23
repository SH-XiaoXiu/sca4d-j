package com.sca4d.app.server.controllers.system;

import com.sca4d.app.server.audit.SystemOperations;
import com.sca4d.app.server.platform.PlatformPrincipalSelection;
import com.sca4d.app.server.service.SystemSettingService;
import com.sca4d.app.server.system.request.ReplaceSettingRequest;
import com.sca4d.app.server.system.view.SystemSettingsView;
import com.sca4d.core.api.annotation.Audit;
import com.sca4d.core.api.annotation.RequirePermission;
import com.sca4d.core.spring.web.ApiResponse;
import com.sca4d.core.spring.web.annotation.CurrentPrincipal;
import com.sca4d.core.spring.web.annotation.IfMatch;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.jetbrains.annotations.NotNull;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Objects;

/** 查询和修改当前应用注册的客户 Settings。 */
@RestController
@RequestMapping("/system/settings")
@Tag(name = "System Settings")
@CurrentPrincipal(selection = PlatformPrincipalSelection.class)
public class SystemSettingController {

    private final SystemSettingService settings;

    /** 创建系统设置 HTTP 入口。 */
    public SystemSettingController(@NotNull SystemSettingService settings) {
        this.settings = Objects.requireNonNull(settings, "系统设置服务不能为空");
    }

    /** @return 当前应用全部设置分组、定义、值和维护来源 */
    @GetMapping
    @Operation(summary = "查询系统设置")
    @RequirePermission(operation = SystemOperations.SETTINGS_READ_VALUE)
    public @NotNull ApiResponse<SystemSettingsView> findAll() {
        return ApiResponse.ok(SystemSettingsView.from(settings.findAll()));
    }

    /** 覆盖一个未由初始化节点同步的设置。 */
    @PutMapping("/{key}")
    @IfMatch
    @Operation(summary = "修改系统设置")
    @RequirePermission(operation = SystemOperations.SETTINGS_MANAGE_VALUE)
    @Audit(operation = "system/setting/replace")
    public @NotNull ApiResponse<SystemSettingsView> replace(
            @PathVariable @NotNull String key,
            @RequestBody @Valid @NotNull ReplaceSettingRequest request) {
        settings.replace(key, request.getValue());
        return ApiResponse.ok(SystemSettingsView.from(settings.findAll()));
    }

    /** 删除一个未由初始化节点同步的数据库值并恢复 Java 默认值。 */
    @DeleteMapping("/{key}")
    @IfMatch
    @Operation(summary = "恢复系统设置默认值")
    @RequirePermission(operation = SystemOperations.SETTINGS_MANAGE_VALUE)
    @Audit(operation = "system/setting/reset")
    public @NotNull ApiResponse<SystemSettingsView> reset(@PathVariable @NotNull String key) {
        settings.reset(key);
        return ApiResponse.ok(SystemSettingsView.from(settings.findAll()));
    }
}
