package com.sca4d.core.spring.web;

import org.jetbrains.annotations.CheckReturnValue;
import org.jetbrains.annotations.Nullable;

import java.util.Optional;

/** 解析 HTTP `Authorization` 中严格的 Bearer Token，不接受空白或其他认证方案。 */
public final class BearerToken {

    private static final String PREFIX = "Bearer ";

    private BearerToken() {
    }

    /**
     * 解析完整的 Authorization Header。
     *
     * @param authorization 完整 Header，可为空
     * @return 格式正确时返回去除 Bearer 前缀的 Token，否则返回空结果
     */
    @CheckReturnValue
    public static Optional<String> parse(@Nullable String authorization) {
        if (authorization == null
                || authorization.length() <= PREFIX.length()
                || !authorization.regionMatches(true, 0, PREFIX, 0, PREFIX.length())) {
            return Optional.empty();
        }
        String token = authorization.substring(PREFIX.length());
        if (token.isBlank() || !token.equals(token.trim())) {
            return Optional.empty();
        }
        return Optional.of(token);
    }
}
