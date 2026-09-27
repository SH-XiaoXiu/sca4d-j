package com.sca4d.app.server.controllers;

import com.fasterxml.jackson.databind.JsonNode;
import com.sca4d.app.server.platform.PlatformPrincipalSelection;
import com.sca4d.app.server.preference.PreferenceService;
import com.sca4d.app.server.preference.request.ReplacePreferenceRequest;
import com.sca4d.app.server.preference.view.PreferenceView;
import com.sca4d.core.api.name.code.TreeCode;
import com.sca4d.core.api.setting.value.SettingValue;
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

/** 管理当前 Platform Principal 的已注册个人偏好。 */
@RestController
@RequestMapping("/me/preferences")
@Tag(name = "Current Principal Preferences")
@CurrentPrincipal(selection = PlatformPrincipalSelection.class)
public class PreferenceController {

    private final PreferenceService preferences;

    /** 创建个人偏好 HTTP 入口。 */
    public PreferenceController(@NotNull PreferenceService preferences) {
        this.preferences = Objects.requireNonNull(preferences, "个人偏好服务不能为空");
    }

    /** 查询当前 Principal 的一个完整偏好，并发布当前 ETag。 */
    @GetMapping("/{key}")
    @Operation(summary = "查询当前 Principal 偏好")
    public @NotNull ApiResponse<PreferenceView> inspect(
            @CurrentPrincipal @NotNull Long principalId,
            @PathVariable @NotNull String key) {
        SettingValue<JsonNode> current = preferences.inspect(principalId, TreeCode.of(key));
        return ApiResponse.ok(PreferenceView.from(current));
    }

    /** 按 `If-Match` 完整替换当前 Principal 的一个偏好。 */
    @PutMapping("/{key}")
    @IfMatch
    @Operation(summary = "替换当前 Principal 偏好")
    public @NotNull ApiResponse<PreferenceView> replace(
            @CurrentPrincipal @NotNull Long principalId,
            @PathVariable @NotNull String key,
            @RequestBody @Valid @NotNull ReplacePreferenceRequest request) {
        SettingValue<JsonNode> replaced = preferences.replace(principalId, TreeCode.of(key), request.getValue());
        return ApiResponse.ok(PreferenceView.from(replaced));
    }

    /** 按 `If-Match` 删除当前 Principal 的偏好覆盖值并恢复默认值。 */
    @DeleteMapping("/{key}")
    @IfMatch
    @Operation(summary = "恢复当前 Principal 偏好默认值")
    public @NotNull ApiResponse<PreferenceView> reset(
            @CurrentPrincipal @NotNull Long principalId,
            @PathVariable @NotNull String key) {
        SettingValue<JsonNode> reset = preferences.reset(principalId, TreeCode.of(key));
        return ApiResponse.ok(PreferenceView.from(reset));
    }
}
