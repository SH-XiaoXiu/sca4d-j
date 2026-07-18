package com.sca4d.file.signature;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sca4d.common.exception.BizException;
import com.sca4d.config.properties.Sca4djFileProperties;
import com.sca4d.file.error.FileErrorCode;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.OffsetDateTime;
import java.util.Base64;
import java.util.Objects;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

/** 签发并校验文件上传、占用和读取使用的短期授权。 */
public class FileGrantSigner {

    private static final String HMAC_ALGORITHM = "HmacSHA256";
    private static final String UPLOAD_GRANT = "UPLOAD";
    private static final String READ_GRANT = "READ";

    private final ObjectMapper objectMapper;
    private final byte[] secret;

    /**
     * 创建使用 HMAC-SHA256 的文件短期授权签名器。
     *
     * @param objectMapper 项目共用 JSON 编解码器
     * @param properties 文件授权密钥配置
     * @throws IllegalStateException 授权密钥少于 32 字节时抛出
     */
    public FileGrantSigner(@NotNull ObjectMapper objectMapper, @NotNull Sca4djFileProperties properties) {
        this.objectMapper = objectMapper;
        this.secret = properties.getGrantSecret().getBytes(StandardCharsets.UTF_8);
        if (secret.length < 32) {
            throw new IllegalStateException("文件授权密钥长度不能少于 32 字节");
        }
    }

    /**
     * 签发绑定文件、Principal、场景和业务资源的上传消费授权。
     *
     * @param fileId 待消费文件主键
     * @param principalId 获得上传资格的 Principal 主键
     * @param scene 业务模块注册的文件签名场景
     * @param resourceId 可选业务资源编码
     * @param expiresAt 授权失效时间
     * @return URL-safe Base64 编码的声明与 HMAC 签名
     */
    public @NotNull String issueUpload(
            @NotNull Long fileId,
            @NotNull Long principalId,
            @NotNull String scene,
            @Nullable String resourceId,
            @NotNull OffsetDateTime expiresAt) {
        FileUploadGrantClaims claims = new FileUploadGrantClaims();
        claims.setGrantType(UPLOAD_GRANT);
        claims.setFileId(fileId);
        claims.setPrincipalId(principalId);
        claims.setScene(scene);
        claims.setResourceId(resourceId);
        claims.setExpiresAtEpochSecond(expiresAt.toEpochSecond());
        return sign(claims);
    }

    /**
     * 校验上传授权签名、绑定数据和有效期。
     *
     * @param token 客户端提交的上传消费授权
     * @param fileId 当前业务准备消费的文件主键
     * @param principalId 当前执行业务写入的 Principal 主键
     * @param scene 当前业务字段声明的签名场景
     * @param resourceId 当前业务字段绑定的可选资源编码
     * @param now 业务事务使用的当前时间
     * @throws BizException 签名无效、任一绑定数据不匹配或授权已经到期时抛出
     */
    public void requireUpload(
            @NotNull String token,
            @NotNull Long fileId,
            @NotNull Long principalId,
            @NotNull String scene,
            @Nullable String resourceId,
            @NotNull OffsetDateTime now) {
        FileUploadGrantClaims claims = read(token, FileUploadGrantClaims.class);
        if (!UPLOAD_GRANT.equals(claims.getGrantType())
                || !fileId.equals(claims.getFileId())
                || !principalId.equals(claims.getPrincipalId())
                || !scene.equals(claims.getScene())
                || !Objects.equals(resourceId, claims.getResourceId())
                || claims.getExpiresAtEpochSecond() <= now.toEpochSecond()) {
            throw new BizException(FileErrorCode.FILE_UPLOAD_GRANT_INVALID);
        }
    }

    /**
     * 签发只绑定文件主键和有效期的读取授权。
     *
     * @param fileId 允许读取的文件主键
     * @param expiresAt 授权失效时间
     * @return URL-safe Base64 编码的声明与 HMAC 签名
     */
    public @NotNull String issueRead(@NotNull Long fileId, @NotNull OffsetDateTime expiresAt) {
        FileReadGrantClaims claims = new FileReadGrantClaims();
        claims.setGrantType(READ_GRANT);
        claims.setFileId(fileId);
        claims.setExpiresAtEpochSecond(expiresAt.toEpochSecond());
        return sign(claims);
    }

    /**
     * 校验读取授权签名、文件绑定和有效期。
     *
     * @param token 客户端提交的文件读取授权
     * @param fileId 当前读取入口请求的文件主键
     * @param now 当前请求时间
     * @throws BizException 签名无效、文件不匹配或授权已经到期时抛出
     */
    public void requireRead(@NotNull String token, @NotNull Long fileId, @NotNull OffsetDateTime now) {
        FileReadGrantClaims claims = read(token, FileReadGrantClaims.class);
        if (!READ_GRANT.equals(claims.getGrantType())
                || !fileId.equals(claims.getFileId())
                || claims.getExpiresAtEpochSecond() <= now.toEpochSecond()) {
            throw new BizException(FileErrorCode.FILE_UPLOAD_GRANT_INVALID);
        }
    }

    /**
     * 将类型化声明序列化后附加 HMAC 签名。
     *
     * @param claims 上传或读取授权声明
     * @return 不含 padding 的 URL-safe token
     * @throws IllegalStateException 项目 JSON 配置无法序列化声明时抛出
     */
    private @NotNull String sign(@NotNull Object claims) {
        try {
            String payload = Base64.getUrlEncoder()
                    .withoutPadding()
                    .encodeToString(objectMapper.writeValueAsBytes(claims));
            return payload + "." + signature(payload);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("文件授权序列化失败", exception);
        }
    }

    /**
     * 验证 token 结构和 HMAC 后反序列化为指定声明类型。
     *
     * <p>签名使用常量时间字节比较；结构、Base64、签名或 JSON 任一无效均收敛为同一业务错误，避免泄漏校验阶段。
     *
     * @param token 客户端提交的文件授权 token
     * @param type 预期声明 Java 类型
     * @param <T> 上传或读取声明类型
     * @return 已验证签名的声明
     * @throws BizException token 无法完整验证和解码时抛出
     */
    private <T> @NotNull T read(@NotNull String token, @NotNull Class<T> type) {
        int separator = token.lastIndexOf('.');
        if (separator <= 0 || separator == token.length() - 1) {
            throw new BizException(FileErrorCode.FILE_UPLOAD_GRANT_INVALID);
        }
        String payload = token.substring(0, separator);
        String actualSignature = token.substring(separator + 1);
        if (!MessageDigest.isEqual(
                actualSignature.getBytes(StandardCharsets.US_ASCII),
                signature(payload).getBytes(StandardCharsets.US_ASCII))) {
            throw new BizException(FileErrorCode.FILE_UPLOAD_GRANT_INVALID);
        }
        try {
            return objectMapper.readValue(Base64.getUrlDecoder().decode(payload), type);
        } catch (IllegalArgumentException | IOException exception) {
            throw new BizException(FileErrorCode.FILE_UPLOAD_GRANT_INVALID);
        }
    }

    /**
     * 计算单段授权载荷的 HMAC-SHA256。
     *
     * @param payload URL-safe Base64 编码的声明载荷
     * @return 不含 padding 的 URL-safe 签名
     * @throws IllegalStateException 当前 JVM 无法初始化约定算法时抛出
     */
    private @NotNull String signature(@NotNull String payload) {
        try {
            Mac mac = Mac.getInstance(HMAC_ALGORITHM);
            mac.init(new SecretKeySpec(secret, HMAC_ALGORITHM));
            return Base64.getUrlEncoder()
                    .withoutPadding()
                    .encodeToString(mac.doFinal(payload.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception exception) {
            throw new IllegalStateException("文件授权签名失败", exception);
        }
    }
}
