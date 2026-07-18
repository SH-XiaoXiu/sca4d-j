package com.sca4d.common.i18n;

import org.jetbrains.annotations.NotNull;
import org.springframework.context.MessageSource;
import org.springframework.context.i18n.LocaleContextHolder;

import java.util.Locale;

/**
 * 后端拥有的错误、校验、目录和审计含义的共用本地化入口。
 *
 * <p>业务判断继续使用错误码、权限码、字典值和审计动作码；本类型只在生成响应或日志时把消息键解析为当前请求语言。
 */
public class LocalizedMessages {

    private final MessageSource messageSource;

    /**
     * 创建使用 Spring MessageSource 解析当前请求语言的实现。
     *
     * @param messageSource Spring Boot 加载的项目消息资源
     */
    public LocalizedMessages(@NotNull MessageSource messageSource) {
        this.messageSource = messageSource;
    }

    /**
     * 按当前请求语言解析固定消息键。
     *
     * @param key 后端代码目录声明的消息键
     * @param arguments 消息模板参数；业务数据保持原文，不在此处自动翻译
     * @return 当前请求语言的最终安全展示文本
     * @throws org.springframework.context.NoSuchMessageException 当前语言和默认资源均缺少消息键时抛出
     */
    public @NotNull String get(@NotNull String key, @NotNull Object... arguments) {
        return get(LocaleContextHolder.getLocale(), key, arguments);
    }

    /**
     * 按明确语言解析固定消息键。
     *
     * @param locale 调用方已经确认支持的语言
     * @param key 后端代码目录声明的消息键
     * @param arguments 消息模板参数；业务数据保持原文，不在此处自动翻译
     * @return 指定语言的最终安全展示文本
     * @throws org.springframework.context.NoSuchMessageException 当前语言和默认资源均缺少消息键时抛出
     */
    public @NotNull String get(
            @NotNull Locale locale, @NotNull String key, @NotNull Object... arguments) {
        return messageSource.getMessage(key, arguments, locale);
    }
}
