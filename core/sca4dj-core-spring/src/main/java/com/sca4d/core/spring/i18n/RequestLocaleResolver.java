package com.sca4d.core.spring.i18n;

import com.sca4d.core.api.i18n.LocaleCatalog;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.springframework.http.HttpHeaders;
import org.springframework.web.servlet.LocaleResolver;

import java.util.Collections;
import java.util.Locale;
import java.util.Objects;

/** 保留 `Accept-Language` 请求 Locale，让动态数据可以使用静态消息目录之外的语言。 */
public class RequestLocaleResolver implements LocaleResolver {

    private final @NotNull Locale defaultLocale;

    /**
     * @param locales 提供请求没有声明语言时使用的应用默认 Locale
     */
    public RequestLocaleResolver(@NotNull LocaleCatalog locales) {
        this.defaultLocale = Objects.requireNonNull(locales, "LocaleCatalog 不能为空").defaultLocale();
    }

    /**
     * 返回请求优先级最高的合法 Locale；没有 `Accept-Language` 时返回应用默认 Locale。
     *
     * @param request 当前 HTTP 请求
     * @return 未按静态消息目录截断的请求 Locale
     */
    @Override
    public @NotNull Locale resolveLocale(@NotNull HttpServletRequest request) {
        HttpServletRequest checked = Objects.requireNonNull(request, "HTTP 请求不能为空");
        if (checked.getHeader(HttpHeaders.ACCEPT_LANGUAGE) == null) {
            return defaultLocale;
        }
        for (Locale requested : Collections.list(checked.getLocales())) {
            Locale normalized = Locale.forLanguageTag(requested.toLanguageTag());
            if (!normalized.getLanguage().isBlank()) {
                return normalized;
            }
        }
        return defaultLocale;
    }

    /**
     * 当前解析器只读取 `Accept-Language`，不允许在请求处理中修改 Locale。
     *
     * @param request 当前 HTTP 请求
     * @param response 当前 HTTP 响应；可能为空
     * @param locale 调用方尝试设置的 Locale；可能为空
     */
    @Override
    public void setLocale(
            @NotNull HttpServletRequest request,
            @Nullable HttpServletResponse response,
            @Nullable Locale locale) {
        throw new UnsupportedOperationException("RequestLocaleResolver 不支持修改请求 Locale");
    }
}
