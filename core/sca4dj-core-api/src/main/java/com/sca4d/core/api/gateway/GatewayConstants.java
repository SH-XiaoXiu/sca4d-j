package com.sca4d.core.api.gateway;

import com.sca4d.core.api.setting.ConfiguredConstants;
import org.jetbrains.annotations.NotNull;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.Locale;
import java.util.Set;

/** 保存 Gateway 与注册 Server 启动时共同确定的 HTTP 协议名称。 */
public final class GatewayConstants {

    /** RFC 7239 的标准转发 Header。 */
    public static final String FORWARDED_HEADER = "Forwarded";

    /** 代理转发 Header 的常见前缀；客户端不能作为可信链自行声明。 */
    public static final String FORWARDED_HEADER_PREFIX = "X-Forwarded-";

    /** 只能由可信 Gateway 写入的控制 Header 前缀。 */
    public static final String HEADER_PREFIX = ConfiguredConstants.text(
            "sca4dj.gateway.protocol.header-prefix",
            "X-Gateway-");

    /** 注册控制请求和可信转发共用的 Secret Header 名称。 */
    public static final String SECRET_HEADER = ConfiguredConstants.text(
            "sca4dj.gateway.protocol.secret-header",
            "X-Gateway-Secret");

    /** Gateway 写入客户端地址的 Header 名称。 */
    public static final String CLIENT_IP_HEADER = ConfiguredConstants.text(
            "sca4dj.gateway.protocol.client-ip-header",
            "X-Gateway-Client-Ip");

    /** Gateway 写入公开路径前缀的 Header 名称。 */
    public static final String EXTERNAL_PREFIX_HEADER = ConfiguredConstants.text(
            "sca4dj.gateway.protocol.external-prefix-header",
            "X-Gateway-External-Prefix");

    /** Gateway 内置 API 路由公开路径前缀。 */
    public static final String API_PREFIX = ConfiguredConstants.text(
            "sca4dj.gateway.protocol.api-prefix",
            "/api");

    /** Gateway 与 Server 共用的回调根路径。 */
    public static final String CALLBACK_PATH = ConfiguredConstants.text(
            "sca4dj.gateway.protocol.callback-path",
            "/callbacks");

    /** Server 租约控制 API 的根路径。 */
    public static final String REGISTRATION_PATH = ConfiguredConstants.text(
            "sca4dj.gateway.protocol.registration-path",
            "/internal/gateway/registrations");

    /** 注册时确认 Gateway 与 Server 使用同一组公开协议名称的摘要。 */
    public static final String FINGERPRINT = fingerprint();

    private GatewayConstants() {
    }

    /** 校验控制 Header 不会与普通代理转发字段混淆，且每个公开路径可直接路由。 */
    public static void validate() {
        if (!HEADER_PREFIX.matches("[!#$%&'*+.^_`|~0-9A-Za-z-]+-")
                || HEADER_PREFIX.regionMatches(true, 0, FORWARDED_HEADER_PREFIX, 0, FORWARDED_HEADER_PREFIX.length())
                || FORWARDED_HEADER_PREFIX.regionMatches(true, 0, HEADER_PREFIX, 0, HEADER_PREFIX.length())) {
            throw new IllegalStateException("Gateway 控制 Header 前缀格式错误或与代理转发前缀冲突");
        }
        Set<String> names = new HashSet<>();
        names.add(header(SECRET_HEADER));
        names.add(header(CLIENT_IP_HEADER));
        names.add(header(EXTERNAL_PREFIX_HEADER));
        if (names.size() != 3) {
            throw new IllegalStateException("Gateway 控制 Header 名称不能重复");
        }
        path(API_PREFIX);
        path(CALLBACK_PATH);
        path(REGISTRATION_PATH);
        if (CALLBACK_PATH.equals(REGISTRATION_PATH)
                || CALLBACK_PATH.startsWith(REGISTRATION_PATH + "/")
                || REGISTRATION_PATH.startsWith(CALLBACK_PATH + "/")) {
            throw new IllegalStateException("Gateway 回调路径和注册路径不能重叠");
        }
    }

    /** 返回用于 HTTP 大小写不敏感比较的规范名称。 */
    private static @NotNull String header(@NotNull String value) {
        if (!value.matches("[!#$%&'*+.^_`|~0-9A-Za-z-]+")
                || value.length() <= HEADER_PREFIX.length()
                || !value.regionMatches(true, 0, HEADER_PREFIX, 0, HEADER_PREFIX.length())) {
            throw new IllegalStateException("Gateway 控制 Header 必须使用配置的前缀: " + value);
        }
        return value.toLowerCase(Locale.ROOT);
    }

    /** 校验可路由的绝对路径前缀。 */
    private static void path(@NotNull String value) {
        if (!value.startsWith("/")
                || value.length() == 1
                || value.endsWith("/")
                || value.contains("//")
                || value.contains("?")
                || value.contains("#")
                || value.chars().anyMatch(Character::isISOControl)) {
            throw new IllegalStateException("Gateway 路径必须是无结尾斜杠的绝对路径: " + value);
        }
    }

    /** 用固定字段顺序计算跨进程配置摘要，不包含 Secret 内容。 */
    private static @NotNull String fingerprint() {
        String contract = String.join(
                "\n",
                HEADER_PREFIX,
                SECRET_HEADER,
                CLIENT_IP_HEADER,
                EXTERNAL_PREFIX_HEADER,
                API_PREFIX,
                CALLBACK_PATH,
                REGISTRATION_PATH);
        try {
            return HexFormat.of()
                    .formatHex(
                            MessageDigest.getInstance("SHA-256").digest(contract.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("运行环境缺少 SHA-256", exception);
        }
    }
}
