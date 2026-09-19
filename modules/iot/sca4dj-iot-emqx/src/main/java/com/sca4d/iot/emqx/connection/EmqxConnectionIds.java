package com.sca4d.iot.emqx.connection;

import com.sca4d.core.api.utils.TextUtils;
import com.sca4d.iot.api.connection.DeviceConnectionId;
import org.jetbrains.annotations.CheckReturnValue;
import org.jetbrains.annotations.NotNull;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.Base64;
import java.util.Objects;

/** 根据 EMQX Client ID 和连接建立时间生成稳定且不暴露公开身份的连接标识。 */
final class EmqxConnectionIds {

    private EmqxConnectionIds() {
    }

    /**
     * @param clientId MQTT Client ID
     * @param connectedAt EMQX 报告的连接建立时间
     * @return 相同连接事实始终得到相同的不透明 `DeviceConnectionId`
     */
    @CheckReturnValue
    static @NotNull DeviceConnectionId from(@NotNull String clientId, @NotNull Instant connectedAt) {
        byte[] identity = TextUtils.requireText(clientId, "EMQX clientId").getBytes(StandardCharsets.UTF_8);
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            digest.update(ByteBuffer.allocate(Integer.BYTES).putInt(identity.length).array());
            digest.update(identity);
            digest.update(
                    ByteBuffer.allocate(Long.BYTES)
                            .putLong(Objects.requireNonNull(connectedAt, "EMQX connectedAt 不能为空").toEpochMilli())
                            .array());
            return new DeviceConnectionId(Base64.getUrlEncoder().withoutPadding().encodeToString(digest.digest()));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("JVM 不支持 SHA-256", exception);
        }
    }
}
