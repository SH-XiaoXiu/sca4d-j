package com.sca4d.iam.totp;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.WriterException;
import com.google.zxing.client.j2se.MatrixToImageWriter;
import com.google.zxing.qrcode.QRCodeWriter;
import org.jetbrains.annotations.NotNull;
import org.springframework.stereotype.Component;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

/** 使用 ZXing 生成只在当前 enrollment 展示的 otpauth PNG 二维码。 */
@Component
public class TotpQrCodeGenerator {

    private static final int SIZE = 320;

    /**
     * 将 issuer、Account 标签和 Base32 密钥编码为标准 otpauth 二维码。
     *
     * @return PNG 字节；调用方不得记录二维码、otpauth URI 或密钥
     */
    public byte @NotNull [] generate(
            @NotNull String issuer, @NotNull String accountLabel, @NotNull String secret) {
        String encodedIssuer = URLEncoder.encode(issuer, StandardCharsets.UTF_8).replace("+", "%20");
        String encodedLabel = URLEncoder.encode(accountLabel, StandardCharsets.UTF_8).replace("+", "%20");
        String uri = "otpauth://totp/"
                + encodedIssuer
                + ":"
                + encodedLabel
                + "?secret="
                + secret
                + "&issuer="
                + encodedIssuer
                + "&algorithm=SHA1&digits=6&period=30";
        try {
            var matrix = new QRCodeWriter().encode(uri, BarcodeFormat.QR_CODE, SIZE, SIZE);
            ByteArrayOutputStream output = new ByteArrayOutputStream();
            MatrixToImageWriter.writeToStream(matrix, "PNG", output);
            return output.toByteArray();
        } catch (WriterException | IOException exception) {
            throw new IllegalStateException("TOTP 二维码生成失败", exception);
        }
    }
}
