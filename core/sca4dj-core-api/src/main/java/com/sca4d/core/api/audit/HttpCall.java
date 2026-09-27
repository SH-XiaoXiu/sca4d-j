package com.sca4d.core.api.audit;

import com.sca4d.core.api.utils.TextUtils;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Optional;

/** 保存审计记录需要的 HTTP 方法、请求路径、客户端地址和 User-Agent 快照。 */
@SuppressWarnings("ClassCanBeRecord") // 项目统一使用普通 class，不使用 Java record。
public class HttpCall {
    /** User-Agent 写入审计记录前允许保留的最大字符数。 */
    public static final int USER_AGENT_MAX_LENGTH = 512;

    private final @NotNull String method;

    private final @NotNull String path;

    private final @NotNull String clientIp;

    private final @Nullable String userAgent;

    /**
     * 创建不包含查询参数和转发 Header 的 HTTP 调用快照。
     *
     * @param method HTTP 方法
     * @param path Servlet 请求路径；出现查询分隔符时只保留其前面的路径
     * @param clientIp 与应用建立连接的客户端地址
     * @param userAgent 可选 User-Agent；控制字符会转换为空格，连续空白会合并并最多保留 512 个字符
     * @throws NullPointerException method、path 或 clientIp 为空时抛出
     * @throws IllegalArgumentException method、path 或 clientIp 为空白文本时抛出
     */
    public HttpCall(
            @NotNull String method,
            @NotNull String path,
            @NotNull String clientIp,
            @Nullable String userAgent) {
        this.method = TextUtils.requireText(method, "HTTP 方法");
        String checkedPath = TextUtils.requireText(path, "HTTP 请求路径");
        int queryStart = checkedPath.indexOf('?');
        this.path = queryStart < 0 ? checkedPath : checkedPath.substring(0, queryStart);
        this.clientIp = TextUtils.requireText(clientIp, "HTTP 客户端地址");
        this.userAgent = cleanUserAgent(userAgent);
    }

    /** @return HTTP 方法 */
    @Contract(pure = true)
    public @NotNull String method() {
        return method;
    }

    /** @return 不包含查询参数的请求路径 */
    @Contract(pure = true)
    public @NotNull String path() {
        return path;
    }

    /** @return 与应用建立连接的客户端地址 */
    @Contract(pure = true)
    public @NotNull String clientIp() {
        return clientIp;
    }

    /** @return 清理并限制长度后的可选 User-Agent */
    @Contract(pure = true)
    public @NotNull Optional<String> userAgent() {
        return Optional.ofNullable(userAgent);
    }

    /** 删除 User-Agent 中的控制字符和重复空白，避免日志字符进入持久化快照。 */
    private static @Nullable String cleanUserAgent(@Nullable String value) {
        if (value == null) {
            return null;
        }
        StringBuilder cleaned = new StringBuilder(Math.min(value.length(), USER_AGENT_MAX_LENGTH));
        boolean pendingSpace = false;
        for (int index = 0; index < value.length() && cleaned.length() < USER_AGENT_MAX_LENGTH; index++) {
            char character = value.charAt(index);
            if (Character.isISOControl(character) || Character.isWhitespace(character)) {
                pendingSpace = cleaned.length() > 0;
                continue;
            }
            if (pendingSpace && cleaned.length() + 1 < USER_AGENT_MAX_LENGTH) {
                cleaned.append(' ');
            }
            pendingSpace = false;
            if (cleaned.length() < USER_AGENT_MAX_LENGTH) {
                cleaned.append(character);
            }
        }
        return cleaned.isEmpty() ? null : cleaned.toString();
    }
}
