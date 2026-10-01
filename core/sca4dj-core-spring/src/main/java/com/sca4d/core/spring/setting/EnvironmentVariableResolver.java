package com.sca4d.core.spring.setting;

import com.sca4d.core.api.isolation.AppName;
import org.jetbrains.annotations.NotNull;
import org.springframework.core.env.Environment;

import java.util.Locale;
import java.util.Objects;

/** 在 Spring 配置边界把中性 ENV 后缀解析为当前应用的实际变量名。 */
public final class EnvironmentVariableResolver {

    private final String prefix;

    /** 根据显式前缀或 spring.application.name 创建解析器。 */
    public EnvironmentVariableResolver(
            @NotNull Environment environment,
            @NotNull EnvironmentPrefixProperties properties) {
        String configured = properties.getPrefix();
        String application = environment.getRequiredProperty("spring.application.name");
        prefix = normalize(
                configured == null || configured.isBlank() ? AppName.normalize(application).toString() : configured);
    }

    /** @return 当前应用使用的环境变量前缀 */
    public @NotNull String prefix() {
        return prefix;
    }

    /** 将 ENV 后缀解析为实际变量名。 */
    public @NotNull String resolve(@NotNull String suffix) {
        String checked = Objects.requireNonNull(suffix, "ENV 后缀不能为空").trim();
        if (!checked.matches("[A-Z][A-Z0-9]*(?:_[A-Z0-9]+)+")) {
            throw new IllegalArgumentException("ENV 后缀格式错误: " + suffix);
        }
        return prefix + "_" + checked;
    }

    private static @NotNull String normalize(@NotNull String value) {
        String normalized = Objects.requireNonNull(value, "ENV 前缀不能为空")
                .trim()
                .toUpperCase(Locale.ROOT)
                .replace('-', '_');
        if (!normalized.matches("[A-Z][A-Z0-9]*(?:_[A-Z0-9]+)*")) {
            throw new IllegalArgumentException("ENV 前缀格式错误: " + value);
        }
        return normalized;
    }
}
