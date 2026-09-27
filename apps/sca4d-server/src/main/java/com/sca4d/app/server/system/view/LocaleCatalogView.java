package com.sca4d.app.server.system.view;

import com.sca4d.core.api.i18n.LocaleCatalog;
import io.swagger.v3.oas.annotations.media.Schema;
import org.jetbrains.annotations.CheckReturnValue;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Unmodifiable;

import java.util.List;
import java.util.Locale;
import java.util.Objects;

/** 返回完整示例 App 支持和默认使用的 BCP 47 Locale 标签。 */
@Schema(name = "LocaleCatalog")
@SuppressWarnings("unused") // Jackson 通过 JavaBean getter 序列化响应。
public final class LocaleCatalogView {

    private final @NotNull List<@NotNull String> supportedLocales;

    private final @NotNull String defaultLocale;

    private LocaleCatalogView(
            @NotNull List<@NotNull String> supportedLocales,
            @NotNull String defaultLocale) {
        this.supportedLocales = List.copyOf(supportedLocales);
        this.defaultLocale = Objects.requireNonNull(defaultLocale, "默认 Locale 不能为空");
    }

    /**
     * 从应用 Locale 目录创建 HTTP 视图。
     *
     * @param locales 应用已经校验的 Locale 目录
     * @return 使用 BCP 47 标签的目录视图
     */
    @CheckReturnValue
    public static @NotNull LocaleCatalogView from(@NotNull LocaleCatalog locales) {
        LocaleCatalog checked = Objects.requireNonNull(locales, "LocaleCatalog 不能为空");
        return new LocaleCatalogView(
                checked.supportedLocales().stream().map(Locale::toLanguageTag).toList(),
                checked.defaultLocale().toLanguageTag());
    }

    /**
     * 返回应用按回退顺序支持的 Locale。
     * @return BCP 47 标签列表
     */
    public @NotNull @Unmodifiable List<@NotNull String> getSupportedLocales() {
        return supportedLocales;
    }

    /**
     * 返回动态国际化字段必须包含的默认 Locale。
     * @return 默认 Locale 的 BCP 47 标签
     */
    public @NotNull String getDefaultLocale() {
        return defaultLocale;
    }
}
