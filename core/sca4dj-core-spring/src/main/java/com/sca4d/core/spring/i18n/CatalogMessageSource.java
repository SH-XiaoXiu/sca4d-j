package com.sca4d.core.spring.i18n;

import com.sca4d.core.api.i18n.LocaleCatalog;
import com.sca4d.core.api.i18n.MessageCatalog;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.springframework.context.MessageSource;
import org.springframework.context.MessageSourceResolvable;
import org.springframework.context.NoSuchMessageException;

import java.util.Locale;
import java.util.Objects;

/** 把 MessageCatalog 接入 Spring 和 Bean Validation 使用的 MessageSource 契约。 */
public class CatalogMessageSource implements MessageSource {

    private final LocaleCatalog locales;

    private final MessageCatalog messages;

    /**
     * 创建委托给模块消息目录的 Spring MessageSource。
     *
     * @param messages 查找消息键和格式化模板的模块静态目录
     * @param locales 把开放请求 Locale 转换为静态消息支持 Locale 的目录
     */
    public CatalogMessageSource(@NotNull MessageCatalog messages, @NotNull LocaleCatalog locales) {
        this.messages = Objects.requireNonNull(messages, "MessageCatalog 不能为空");
        this.locales = Objects.requireNonNull(locales, "LocaleCatalog 不能为空");
    }

    /** 按 Spring MessageSource 约定读取消息，不存在时返回调用方默认值；Locale 为空时使用 JVM 默认值。 */
    @Override
    public String getMessage(
            @NotNull String code,
            Object @Nullable [] arguments,
            @Nullable String defaultMessage,
            @Nullable Locale locale) {
        Locale checkedLocale = supported(locale);
        return messages.find(code, checkedLocale, arguments == null ? new Object[0] : arguments)
                .orElse(defaultMessage);
    }

    /** 按 Spring MessageSource 约定读取必需消息；Locale 为空时使用 JVM 默认值。 */
    @Override
    public @NotNull String getMessage(
            @NotNull String code,
            Object @Nullable [] arguments,
            @Nullable Locale locale) {
        Locale checkedLocale = supported(locale);
        return messages.find(code, checkedLocale, arguments == null ? new Object[0] : arguments)
                .orElseThrow(() -> new NoSuchMessageException(code, checkedLocale));
    }

    /** 按 MessageSourceResolvable 中的候选键顺序读取消息；Locale 为空时使用 JVM 默认值。 */
    @Override
    public @NotNull String getMessage(
            @NotNull MessageSourceResolvable resolvable,
            @Nullable Locale locale) {
        Locale checkedLocale = supported(locale);
        String[] codes = resolvable.getCodes();
        Object[] arguments = resolvable.getArguments();
        if (codes != null) {
            for (String code : codes) {
                var resolved = messages.find(code, checkedLocale, arguments == null ? new Object[0] : arguments);
                if (resolved.isPresent()) {
                    return resolved.get();
                }
            }
        }
        String defaultMessage = resolvable.getDefaultMessage();
        if (defaultMessage != null) {
            return defaultMessage;
        }
        throw new NoSuchMessageException(codes == null || codes.length == 0 ? "" : codes[0], checkedLocale);
    }

    /** 把 Spring 提交的开放请求 Locale 转换为静态消息目录支持的 Locale。 */
    private @NotNull Locale supported(@Nullable Locale locale) {
        return locales.resolve(locale == null ? Locale.getDefault() : locale);
    }
}
