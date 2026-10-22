package com.sca4d.core.spring.web;

import com.sca4d.core.api.gateway.GatewayConstants;
import com.sca4d.core.api.setting.ConfiguredConstants;

import java.util.HashSet;
import java.util.Locale;
import java.util.Set;

/** 保存每个 HTTP Server 进程启动时确定的自定义请求和响应 Header 名称。 */
public final class HttpConstants {

    private static final Set<String> RESERVED = Set.of(
            "accept",
            "accept-language",
            "access-control-expose-headers",
            "authorization",
            "cache-control",
            "connection",
            "content-length",
            "content-type",
            "date",
            "etag",
            "forwarded",
            "host",
            "if-match",
            "if-none-match",
            "last-event-id",
            "location",
            "retry-after",
            "transfer-encoding",
            "vary");

    /** 请求和响应使用的调用标识 Header。 */
    public static final String CALL_ID_HEADER = ConfiguredConstants.text(
            "sca4dj.http.headers.call-id",
            "X-Call-Id");

    /** HTTP 调用方提交幂等键的 Header。 */
    public static final String IDEMPOTENCY_KEY_HEADER = ConfiguredConstants.text(
            "sca4dj.http.headers.idempotency-key",
            "Idempotency-Key");

    /** 客户端收到后重新读取指定本地状态的响应 Header。 */
    public static final String CLIENT_STATE_REFRESH_HEADER = ConfiguredConstants.text(
            "sca4dj.http.headers.client-state-refresh",
            "X-Client-State-Refresh");

    /** 应用启动时确定的版本协议头：client-name。 */
    public static final String CLIENT_NAME_HEADER = ConfiguredConstants.text(
            "sca4dj.http.headers.client-name",
            "X-Client-Name");

    /** 应用启动时确定的版本协议头：client-release-version。 */
    public static final String CLIENT_RELEASE_VERSION_HEADER = ConfiguredConstants.text(
            "sca4dj.http.headers.client-release-version",
            "X-Client-Release-Version");

    /** 应用启动时确定的版本协议头：client-release-id。 */
    public static final String CLIENT_RELEASE_ID_HEADER = ConfiguredConstants.text(
            "sca4dj.http.headers.client-release-id",
            "X-Client-Release-Id");

    /** 应用启动时确定的版本协议头：client-build。 */
    public static final String CLIENT_BUILD_HEADER = ConfiguredConstants.text(
            "sca4dj.http.headers.client-build",
            "X-Client-Build");

    /** 应用启动时确定的版本协议头：client-api-contract。 */
    public static final String CLIENT_API_CONTRACT_HEADER = ConfiguredConstants.text(
            "sca4dj.http.headers.client-api-contract",
            "X-Client-Api-Contract");

    /** 应用启动时确定的版本协议头：server-release-version。 */
    public static final String SERVER_RELEASE_VERSION_HEADER = ConfiguredConstants.text(
            "sca4dj.http.headers.server-release-version",
            "X-Server-Release-Version");

    /** 应用启动时确定的版本协议头：server-release-id。 */
    public static final String SERVER_RELEASE_ID_HEADER = ConfiguredConstants.text(
            "sca4dj.http.headers.server-release-id",
            "X-Server-Release-Id");

    /** 应用启动时确定的版本协议头：server-build。 */
    public static final String SERVER_BUILD_HEADER = ConfiguredConstants.text(
            "sca4dj.http.headers.server-build",
            "X-Server-Build");

    /** 应用启动时确定的版本协议头：server-api-contract。 */
    public static final String SERVER_API_CONTRACT_HEADER = ConfiguredConstants.text(
            "sca4dj.http.headers.server-api-contract",
            "X-Server-Api-Contract");

    /** 应用启动时确定的版本协议头：compatibility-status。 */
    public static final String COMPATIBILITY_STATUS_HEADER = ConfiguredConstants.text(
            "sca4dj.http.headers.compatibility-status",
            "X-Compatibility-Status");

    /** 应用启动时确定的版本协议头：api-contract。 */
    public static final String API_CONTRACT_HEADER = ConfiguredConstants.text(
            "sca4dj.http.headers.api-contract",
            "X-Api-Contract");

    private HttpConstants() {
    }

    /** 校验自定义 Header 可以被 HTTP 容器接受且大小写不敏感地互不重复。 */
    public static void validate() {
        Set<String> names = new HashSet<>();
        for (String name : new String[] {CALL_ID_HEADER, IDEMPOTENCY_KEY_HEADER, CLIENT_STATE_REFRESH_HEADER,
                CLIENT_NAME_HEADER,
                CLIENT_RELEASE_VERSION_HEADER,
                CLIENT_RELEASE_ID_HEADER,
                CLIENT_BUILD_HEADER,
                CLIENT_API_CONTRACT_HEADER,
                SERVER_RELEASE_VERSION_HEADER,
                SERVER_RELEASE_ID_HEADER,
                SERVER_BUILD_HEADER,
                SERVER_API_CONTRACT_HEADER,
                COMPATIBILITY_STATUS_HEADER,
                API_CONTRACT_HEADER}) {
            if (!name.matches("[!#$%&'*+.^_`|~0-9A-Za-z-]+")
                    || !names.add(name.toLowerCase(Locale.ROOT))
                    || reserved(name)
                    || name.regionMatches(
                            true,
                            0,
                            GatewayConstants.HEADER_PREFIX,
                            0,
                            GatewayConstants.HEADER_PREFIX.length())) {
                throw new IllegalStateException("Core HTTP 自定义 Header 名称无效或重复: " + name);
            }
        }
    }

    /** 返回名称是否已经由 HTTP 协议或 Core 的条件请求使用。 */
    public static boolean reserved(String name) {
        return RESERVED.contains(name.toLowerCase(Locale.ROOT));
    }
}
