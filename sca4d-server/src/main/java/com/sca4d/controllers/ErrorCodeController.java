package com.sca4d.controllers;

import com.sca4d.common.web.ApiResponse;
import com.sca4d.system.ErrorCodeService;
import com.sca4d.system.view.ErrorCodeCatalogView;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.jetbrains.annotations.NotNull;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 错误码接口。
 */
@RestController
@RequestMapping("/system/error-codes")
@RequiredArgsConstructor
@Tag(name = "系统-错误码", description = "错误码目录与号段查询")
public class ErrorCodeController {

    private final ErrorCodeService service;

    /**
     * 获取错误码目录。
     *
     * @return 错误码目录响应
     */
    @GetMapping
    @Operation(summary = "获取错误码目录", description = "返回当前应用已注册的错误码列表和号段占用信息。")
    public @NotNull ApiResponse<ErrorCodeCatalogView> catalog() {
        return ApiResponse.ok(ErrorCodeCatalogView.from(service.catalog()));
    }
}
