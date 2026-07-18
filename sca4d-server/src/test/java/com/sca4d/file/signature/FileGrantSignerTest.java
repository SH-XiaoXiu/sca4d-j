package com.sca4d.file.signature;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sca4d.common.exception.BizException;
import com.sca4d.config.properties.Sca4djFileProperties;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.Test;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;

/** 验证文件授权的签发、绑定字段、过期和篡改拒绝。 */
class FileGrantSignerTest {

    @Test
    void shouldBindUploadGrantToPrincipalSceneAndResource() {
        FileGrantSigner signer = signer();
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        String grant = signer.issueUpload(10L, 20L, "iam.default-profile.avatar", "30", now.plusMinutes(10));

        assertThatCode(() -> signer.requireUpload(
                grant, 10L, 20L, "iam.default-profile.avatar", "30", now))
                .doesNotThrowAnyException();
        assertThatThrownBy(() -> signer.requireUpload(
                grant, 10L, 20L, "product.product.cover", "30", now))
                .isInstanceOf(BizException.class);
        assertThatThrownBy(() -> signer.requireUpload(
                grant, 10L, 20L, "iam.default-profile.avatar", "31", now))
                .isInstanceOf(BizException.class);
    }

    @Test
    void shouldRejectUploadGrantAtExactExpiry() {
        FileGrantSigner signer = signer();
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        String grant = signer.issueUpload(10L, 20L, "iam.default-profile.avatar", "30", now);

        assertThatThrownBy(() -> signer.requireUpload(
                grant, 10L, 20L, "iam.default-profile.avatar", "30", now))
                .isInstanceOf(BizException.class);
    }

    @Test
    void shouldRejectReadGrantAtExactExpiry() {
        FileGrantSigner signer = signer();
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        String grant = signer.issueRead(10L, now);

        assertThatThrownBy(() -> signer.requireRead(grant, 10L, now))
                .isInstanceOf(BizException.class);
    }

    @Test
    void shouldRejectUploadGrantAsReadGrant() {
        FileGrantSigner signer = signer();
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        String grant = signer.issueUpload(10L, 20L, "iam.default-profile.avatar", "30", now.plusMinutes(10));

        assertThatThrownBy(() -> signer.requireRead(grant, 10L, now))
                .isInstanceOf(BizException.class);
    }

    @Test
    void shouldRejectReadGrantAsUploadGrant() {
        FileGrantSigner signer = signer();
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        String grant = signer.issueRead(10L, now.plusMinutes(10));

        assertThatThrownBy(() -> signer.requireUpload(
                grant, 10L, 20L, "iam.default-profile.avatar", "30", now))
                .isInstanceOf(BizException.class);
    }

    /**
     * 创建使用固定 32 字节测试密钥的文件授权签名器。
     *
     * @return 与生产算法一致且不依赖外部配置的签名器
     */
    private @NotNull FileGrantSigner signer() {
        Sca4djFileProperties properties = new Sca4djFileProperties();
        properties.setGrantSecret("0123456789abcdef0123456789abcdef");
        return new FileGrantSigner(new ObjectMapper(), properties);
    }
}
