package com.sca4d.app.server.controllers;

import com.sca4d.app.server.system.view.OperationCatalogView;
import com.sca4d.core.api.permission.OperationCatalog;
import com.sca4d.core.spring.web.ApiResponse;
import org.jetbrains.annotations.NotNull;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Objects;

/** 返回完整示例 App 已安装的 Operation 目录。 */
@RestController
@RequestMapping("/system/permissions")
public class OperationController {

    private final OperationCatalog catalog;

    /**
     * 创建 Operation 目录入口。
     *
     * @param catalog 应用已经安装并校验的 Operation 目录
     */
    public OperationController(@NotNull OperationCatalog catalog) {
        this.catalog = Objects.requireNonNull(catalog, "Operation 目录不能为空");
    }

    /**
     * 读取当前应用的 Operation 目录。
     *
     * @return 名称按当前请求语言解析的目录树和 ACTION 列表
     */
    @GetMapping
    public @NotNull ApiResponse<OperationCatalogView> operations() {
        return ApiResponse.ok(OperationCatalogView.from(catalog));
    }
}
