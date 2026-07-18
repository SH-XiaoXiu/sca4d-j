package com.sca4d.common.i18n.view;

import com.sca4d.common.i18n.SupportedLocale;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.experimental.Accessors;
import org.jetbrains.annotations.NotNull;

/** 管理端应用和多语言编辑器共用的语言目录项。 */
@Data
@Accessors(chain = true)
@Schema(description = "支持语言目录项")
public class LanguageView {

    private String code;
    private String nativeName;
    private Boolean defaultLanguage;
    private Boolean required;

    /**
     * 把后端共用语言定义转换为公开目录项。
     *
     * @param language 已通过启动契约校验的支持语言
     * @return 语言代码、自称、默认和必填标记
     */
    public static @NotNull LanguageView from(@NotNull SupportedLocale language) {
        return new LanguageView()
                .setCode(language.value())
                .setNativeName(language.description())
                .setDefaultLanguage(language.isDefaultLanguage())
                .setRequired(language.isRequired());
    }
}
