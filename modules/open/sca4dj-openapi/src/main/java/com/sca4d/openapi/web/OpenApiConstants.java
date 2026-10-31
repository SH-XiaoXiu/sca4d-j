package com.sca4d.openapi.web;

import com.sca4d.core.api.gateway.GatewayConstants;
import com.sca4d.core.api.setting.ConfiguredConstants;
import com.sca4d.core.spring.web.HttpConstants;

import java.util.HashSet;
import java.util.Locale;
import java.util.Set;

/** 保存 OpenAPI 路由与 HMAC 请求在进程启动时固定的公开名称。 */
public final class OpenApiConstants {

    /** OpenAPI 路由定义的公开路径必须位于此路径之下。 */
    public static final String PATH_PREFIX = ConfiguredConstants.text(
            "sca4dj.openapi.path-prefix",
            "/openapi");

    /** HMAC Client 标识请求头。 */
    public static final String CLIENT_ID_HEADER = ConfiguredConstants.text(
            "sca4dj.openapi.hmac.headers.client-id",
            "X-OpenApi-Client-Id");

    /** HMAC Unix 秒请求头。 */
    public static final String TIMESTAMP_HEADER = ConfiguredConstants.text(
            "sca4dj.openapi.hmac.headers.timestamp",
            "X-OpenApi-Timestamp");

    /** HMAC 一次性随机数请求头。 */
    public static final String NONCE_HEADER = ConfiguredConstants.text(
            "sca4dj.openapi.hmac.headers.nonce",
            "X-OpenApi-Nonce");

    /** HMAC 无填充 Base64URL 签名请求头。 */
    public static final String SIGNATURE_HEADER = ConfiguredConstants.text(
            "sca4dj.openapi.hmac.headers.signature",
            "X-OpenApi-Signature");

    private OpenApiConstants() {
    }

    /** 校验 OpenAPI 路径和全部 HMAC Header 名称。 */
    public static void validate() {
        if (!PATH_PREFIX.startsWith("/")
                || PATH_PREFIX.length() == 1
                || PATH_PREFIX.endsWith("/")
                || PATH_PREFIX.contains("//")
                || PATH_PREFIX.contains("?")
                || PATH_PREFIX.contains("#")) {
            throw new IllegalStateException("OpenAPI 路径前缀必须是无结尾斜杠的绝对路径");
        }
        Set<String> names = new HashSet<>();
        for (String name : new String[] {CLIENT_ID_HEADER, TIMESTAMP_HEADER, NONCE_HEADER, SIGNATURE_HEADER}) {
            if (!name.matches("[!#$%&'*+.^_`|~0-9A-Za-z-]+")
                    || !names.add(name.toLowerCase(Locale.ROOT))
                    || HttpConstants.reserved(name)
                    || name.regionMatches(
                            true,
                            0,
                            GatewayConstants.HEADER_PREFIX,
                            0,
                            GatewayConstants.HEADER_PREFIX.length())) {
                throw new IllegalStateException("OpenAPI HMAC Header 名称无效或重复: " + name);
            }
        }
    }
}
