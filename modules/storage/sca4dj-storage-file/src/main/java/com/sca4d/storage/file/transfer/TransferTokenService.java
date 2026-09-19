package com.sca4d.storage.file.transfer;

import com.sca4d.core.api.error.BusinessException;
import com.sca4d.core.api.runtime.RuntimeNamespace;
import com.sca4d.core.api.security.EncryptedSecret;
import com.sca4d.core.api.security.SecretCipher;
import com.sca4d.core.api.security.SecretKeyring;
import com.sca4d.core.api.storage.FileErrorCode;
import com.sca4d.core.api.storage.StorageTransferSigner;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.util.Base64;
import java.util.Objects;

/** 使用 SecretKeyring 加密本地 PUT 和 GET 的动作、文件标识及到期时间。 */
@ApiStatus.Internal
public final class TransferTokenService implements StorageTransferSigner {
    private final @NotNull SecretKeyring keyring;

    private final @NotNull URI baseUrl;

    private final @NotNull Clock clock;

    private final @NotNull String purpose;

    /** 创建与当前应用命名空间绑定的本地文件传输令牌签发器。 */
    public TransferTokenService(
            @NotNull SecretKeyring keyring,
            @NotNull URI baseUrl,
            @NotNull Clock clock,
            @NotNull RuntimeNamespace runtimeNamespace) {
        this.keyring = Objects.requireNonNull(keyring, "Secret keyring 不能为空");
        this.baseUrl = Objects.requireNonNull(baseUrl, "文件传输公开地址不能为空");
        this.clock = Objects.requireNonNull(clock, "时钟不能为空");
        this.purpose = Objects.requireNonNull(runtimeNamespace, "运行时命名空间不能为空")
                .sca4dj("storage", "local-transfer", "v1");
    }

    @Override
    public @NotNull URI uploadUrl(long fileId, @NotNull Instant expiresAt) {
        return url("uploads", token("PUT", fileId, expiresAt));
    }

    @Override
    public @NotNull URI accessUrl(long fileId, @NotNull Instant expiresAt) {
        return url("objects", token("GET", fileId, expiresAt));
    }

    /** 校验传输动作、到期时间和密文内容，并返回令牌中的文件标识。 */
    public long verify(@NotNull String expectedAction, @NotNull String token) {
        try {
            String[] encoded = token.split("\\.", 2);
            if (encoded.length != 2)
                throw new IllegalArgumentException("token shape");
            SecretCipher cipher = keyring.findCipher(decodeText(encoded[0]))
                    .orElseThrow(() -> new IllegalArgumentException("unknown key"));
            byte[] ciphertext = Base64.getUrlDecoder().decode(encoded[1]);
            String[] claims = new String(
                    cipher.decrypt(purpose, new EncryptedSecret(cipher.keyId(), ciphertext)),
                    StandardCharsets.UTF_8)
                    .split("\\|", 3);
            if (claims.length != 3 || !expectedAction.equals(claims[0]))
                throw new IllegalArgumentException("action");
            Instant expiresAt = Instant.ofEpochSecond(Long.parseLong(claims[2]));
            if (!clock.instant().isBefore(expiresAt)) {
                FileErrorCode errorCode = "PUT".equals(expectedAction)
                        ? FileErrorCode.UPLOAD_EXPIRED
                        : FileErrorCode.TRANSFER_INVALID;
                throw new BusinessException(errorCode);
            }
            long fileId = Long.parseLong(claims[1]);
            if (fileId <= 0)
                throw new IllegalArgumentException("file id");
            return fileId;
        } catch (BusinessException exception) {
            throw exception;
        } catch (RuntimeException exception) {
            throw new BusinessException(FileErrorCode.TRANSFER_INVALID, exception);
        }
    }

    /** 加密传输动作、文件标识和到期秒数，并返回包含 key ID 的 URL-safe token。 */
    private @NotNull String token(@NotNull String action, long fileId, @NotNull Instant expiresAt) {
        if (fileId <= 0)
            throw new IllegalArgumentException("文件标识必须为正数");
        String claims = action + "|" + fileId + "|" + expiresAt.getEpochSecond();
        EncryptedSecret encrypted = keyring.activeCipher().encrypt(purpose, claims.getBytes(StandardCharsets.UTF_8));
        return encodeText(encrypted.keyId()) + "."
                + Base64.getUrlEncoder().withoutPadding().encodeToString(encrypted.ciphertext());
    }

    /** 把传输 token 附加到配置的公开文件传输地址。 */
    private @NotNull URI url(@NotNull String path, @NotNull String token) {
        String prefix = baseUrl.toString();
        URI directory = URI.create(prefix.endsWith("/") ? prefix : prefix + "/");
        return URI.create(directory.resolve("files/transfers/" + path) + "?token=" + token);
    }

    /** 使用不带填充的 Base64URL 编码 key ID。 */
    private static @NotNull String encodeText(@NotNull String value) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(value.getBytes(StandardCharsets.UTF_8));
    }

    /** 从 Base64URL 还原 token 中的 key ID。 */
    private static @NotNull String decodeText(@NotNull String value) {
        return new String(Base64.getUrlDecoder().decode(value), StandardCharsets.UTF_8);
    }
}
