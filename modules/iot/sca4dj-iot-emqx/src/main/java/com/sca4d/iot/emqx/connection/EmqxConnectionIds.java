package com.sca4d.iot.emqx.connection;

import cn.hutool.crypto.digest.DigestUtil;
import com.sca4d.core.api.utils.TextUtils;
import com.sca4d.iot.api.connection.DeviceConnectionId;
import org.jetbrains.annotations.CheckReturnValue;
import org.jetbrains.annotations.NotNull;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;
import java.util.Objects;

/** 根据 EMQX Client ID 和连接建立时间生成稳定且不暴露公开身份的连接标识。 */
class EmqxConnectionIds {

    private EmqxConnectionIds() {
    }

    /**
     * @param clientId MQTT Client ID
     * @param connectedAt EMQX 报告的连接建立时间
     * @return 相同连接信息始终得到相同的不透明 `DeviceConnectionId`
     */
    @CheckReturnValue
    static @NotNull DeviceConnectionId from(@NotNull String clientId, @NotNull Instant connectedAt) {
        byte[] identity = TextUtils.requireText(clientId, "EMQX clientId").getBytes(StandardCharsets.UTF_8);
        byte[] canonical = ByteBuffer.allocate(Integer.BYTES + identity.length + Long.BYTES)
                .putInt(identity.length)
                .put(identity)
                .putLong(Objects.requireNonNull(connectedAt, "EMQX connectedAt 不能为空").toEpochMilli())
                .array();
        return new DeviceConnectionId(
                Base64.getUrlEncoder().withoutPadding().encodeToString(DigestUtil.sha256(canonical)));
    }
}
