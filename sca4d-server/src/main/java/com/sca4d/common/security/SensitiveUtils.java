package com.sca4d.common.security;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/** 对日志、审计和提示信息中的敏感标识执行固定脱敏规则。 */
public final class SensitiveUtils {

    private SensitiveUtils() {
    }

    /** @return 只保留首尾字符的标识提示；空值固定返回三个星号 */
    public static @NotNull String identifier(@Nullable String value) {
        if (value == null || value.isBlank()) {
            return "***";
        }
        String normalized = value.trim();
        if (normalized.length() == 1) {
            return "*";
        }
        if (normalized.length() == 2) {
            return normalized.charAt(0) + "*";
        }
        return normalized.charAt(0) + "***" + normalized.charAt(normalized.length() - 1);
    }

    /** @return 保留本地部分前两位和域名的邮箱提示；格式无效时固定返回三个星号 */
    public static @NotNull String email(@Nullable String value) {
        if (value == null || value.isBlank()) {
            return "***";
        }
        String normalized = value.trim();
        int at = normalized.indexOf('@');
        if (at <= 0 || at == normalized.length() - 1) {
            return "***";
        }
        String local = normalized.substring(0, at);
        String visible = local.length() == 1 ? local : local.substring(0, 2);
        return visible + "***" + normalized.substring(at);
    }
}
