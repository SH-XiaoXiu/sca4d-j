package com.sca4d.app.server.authentication;

import cn.hutool.extra.qrcode.QrCodeUtil;
import cn.hutool.extra.qrcode.QrConfig;
import org.jetbrains.annotations.NotNull;
import org.springframework.stereotype.Component;

import java.util.Objects;

/** 使用 Hutool 把瞬时 otpauth URI 编码为不持久化的 PNG。 */
@Component
public final class TotpQrCodeGenerator {

    private static final int SIZE = 320;

    /** 创建 TOTP PNG 二维码生成器。 */
    public TotpQrCodeGenerator() {
    }

    /**
     * 把 provisioning URI 编码为 PNG。
     *
     * @param provisioningUri 含 TOTP Secret 的瞬时 URI
     * @return 只供当前 HTTP 响应使用的 PNG 字节
     */
    public byte @NotNull [] generate(@NotNull String provisioningUri) {
        String uri = Objects.requireNonNull(provisioningUri, "TOTP provisioning URI 不能为空");
        return QrCodeUtil.generatePng(uri, new QrConfig(SIZE, SIZE).setMargin(2));
    }
}
