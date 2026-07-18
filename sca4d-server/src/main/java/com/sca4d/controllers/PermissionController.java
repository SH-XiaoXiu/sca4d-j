package com.sca4d.controllers;

import com.sca4d.common.auth.annotation.AnonymousAccess;
import com.sca4d.common.i18n.LocalizedMessages;
import com.sca4d.common.permission.PermissionCatalog;
import com.sca4d.common.web.ApiResponse;
import com.sca4d.system.view.PermissionCatalogView;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.jetbrains.annotations.NotNull;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 权限目录接口。
 */
@RestController
@RequestMapping("/system/permissions")
@AnonymousAccess
@RequiredArgsConstructor
@Tag(name = "系统-权限", description = "权限树与权限目录查询")
public class PermissionController {

    private final PermissionCatalog catalog;
    private final LocalizedMessages messages;

    /**
     * 获取权限目录。
     *
     * @return 权限目录响应
     */
    @GetMapping
    @Operation(summary = "获取权限目录", description = "返回当前应用已注册的权限树和权限扁平列表。")
    public @NotNull ApiResponse<PermissionCatalogView> catalog() {
        return ApiResponse.ok(PermissionCatalogView.from(catalog, messages));
    }
}
