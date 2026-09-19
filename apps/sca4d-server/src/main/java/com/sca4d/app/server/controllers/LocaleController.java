package com.sca4d.app.server.controllers;

import com.sca4d.app.server.system.view.LocaleCatalogView;
import com.sca4d.core.api.i18n.LocaleCatalog;
import com.sca4d.core.spring.web.ApiResponse;
import org.jetbrains.annotations.NotNull;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Objects;

/** 返回完整示例 App 支持和默认使用的 Locale。 */
@RestController
@RequestMapping("/system/locales")
public class LocaleController {

    private final @NotNull LocaleCatalog locales;

    /**
     * 创建 Locale 目录 HTTP 入口。
     *
     * @param locales 应用已经校验的 Locale 目录
     */
    public LocaleController(@NotNull LocaleCatalog locales) {
        this.locales = Objects.requireNonNull(locales, "LocaleCatalog 不能为空");
    }

    /**
     * 读取当前应用支持和默认使用的 Locale。
     *
     * @return 使用稳定 BCP 47 标签的 Locale 目录
     */
    @GetMapping
    public @NotNull ApiResponse<LocaleCatalogView> locales() {
        return ApiResponse.ok(LocaleCatalogView.from(locales));
    }
}
