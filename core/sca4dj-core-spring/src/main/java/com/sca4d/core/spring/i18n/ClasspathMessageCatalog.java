package com.sca4d.core.spring.i18n;

import com.sca4d.core.api.i18n.LocaleCatalog;
import com.sca4d.core.api.i18n.MessageCatalog;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.ResourcePatternResolver;

import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.text.MessageFormat;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Properties;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

/** 从所有模块的标准资源目录读取消息，并拒绝同一 Locale 的重复键。 */
public class ClasspathMessageCatalog implements MessageCatalog {

    private static final Logger LOG = LoggerFactory.getLogger(ClasspathMessageCatalog.class);

    private static final String RESOURCE_PATTERN = "classpath*:META-INF/sca4dj/i18n/*/*.properties";

    private static final int MISSING_WARNING_LIMIT = 256;

    private final Locale defaultLocale;

    private final Map<Locale, Map<String, String>> messages;

    private final Set<String> warnedMissingKeys = ConcurrentHashMap.newKeySet();

    private final AtomicBoolean warningLimitReported = new AtomicBoolean();

    /**
     * 扫描并加载所有模块消息资源。
     *
     * @param resources Spring 类路径资源解析器
     * @param locales 应用支持和默认 Locale
     */
    public ClasspathMessageCatalog(
            @NotNull ResourcePatternResolver resources,
            @NotNull LocaleCatalog locales) {
        defaultLocale = locales.defaultLocale();
        messages = load(resources);
    }

    /**
     * 读取并格式化静态消息。
     *
     * @param key 稳定消息键
     * @param locale 要读取的 Locale
     * @param arguments 消息模板参数
     * @return 找到消息时返回格式化结果，否则返回空
     */
    @Override
    public @NotNull Optional<String> find(
            @NotNull String key,
            @NotNull Locale locale,
            @NotNull Object... arguments) {
        String pattern = messages.getOrDefault(locale, Map.of()).get(key);
        if (pattern != null) {
            return Optional.of(new MessageFormat(pattern, locale).format(arguments));
        }
        if (locale.equals(defaultLocale)) {
            warnMissing(key);
        }
        return Optional.empty();
    }

    /** 加载资源并检查每个 Locale 下的键唯一性。 */
    private static Map<Locale, Map<String, String>> load(ResourcePatternResolver resolver) {
        Map<Locale, Map<String, String>> loaded = new LinkedHashMap<>();
        try {
            for (Resource resource : resolver.getResources(RESOURCE_PATTERN)) {
                String filename = resource.getFilename();
                if (filename == null || !filename.endsWith(".properties")) {
                    throw new IllegalStateException("无法识别 I18n 资源文件名: " + resource.getDescription());
                }
                String languageTag = filename.substring(0, filename.length() - ".properties".length());
                Locale locale = Locale.forLanguageTag(languageTag);
                if (locale.getLanguage().isBlank()) {
                    throw new IllegalStateException("I18n 资源语言标签不正确: " + resource.getDescription());
                }
                Properties properties = new Properties();
                try (InputStreamReader reader = new InputStreamReader(
                        resource.getInputStream(),
                        StandardCharsets.UTF_8)) {
                    properties.load(reader);
                }
                Map<String, String> localeMessages = loaded.computeIfAbsent(locale, ignored -> new LinkedHashMap<>());
                for (String key : properties.stringPropertyNames()) {
                    if (localeMessages.putIfAbsent(key, properties.getProperty(key)) != null) {
                        throw new IllegalStateException(
                                "I18n 消息键重复: locale="
                                        + locale.toLanguageTag() + " key=" + key + " resource="
                                        + resource.getDescription());
                    }
                }
            }
        } catch (IOException exception) {
            throw new IllegalStateException("读取模块 I18n 资源失败", exception);
        }
        Map<Locale, Map<String, String>> immutable = new LinkedHashMap<>();
        loaded.forEach((locale, values) -> immutable.put(locale, Collections.unmodifiableMap(values)));
        return Collections.unmodifiableMap(immutable);
    }

    /** 对最终缺失消息键限量记录一次警告。 */
    private void warnMissing(String key) {
        if (warnedMissingKeys.contains(key)) {
            return;
        }
        if (warnedMissingKeys.size() >= MISSING_WARNING_LIMIT) {
            if (warningLimitReported.compareAndSet(false, true)) {
                LOG.warn("I18n 缺失消息警告已达到限制 | limit={}", MISSING_WARNING_LIMIT);
            }
            return;
        }
        if (warnedMissingKeys.add(key)) {
            LOG.warn("I18n 消息键在请求和默认 Locale 中均不存在 | key={} defaultLocale={}", key, defaultLocale);
        }
    }
}
