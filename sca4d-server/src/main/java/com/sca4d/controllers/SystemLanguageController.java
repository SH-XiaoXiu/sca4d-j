package com.sca4d.controllers;

import com.sca4d.common.auth.annotation.AnonymousAccess;
import com.sca4d.common.i18n.SupportedLocale;
import com.sca4d.common.i18n.view.LanguageView;
import com.sca4d.common.web.ApiResponse;
import org.jetbrains.annotations.NotNull;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Arrays;
import java.util.List;

/** 前后端本地化和多语言内容编辑共用的语言目录接口。 */
@RestController
@RequestMapping("/system/languages")
public class SystemLanguageController {

    /**
     * 读取当前发布版本支持的完整语言目录。
     *
     * @return 按产品展示顺序排列的语言代码、自称、默认和必填标记
     */
    @GetMapping
    @AnonymousAccess
    public @NotNull ApiResponse<List<LanguageView>> languages() {
        return ApiResponse.ok(Arrays.stream(SupportedLocale.values())
                .map(LanguageView::from)
                .toList());
    }
}
