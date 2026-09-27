package com.sca4d.app.server.iot;

import static org.springframework.http.HttpStatus.UNAUTHORIZED;

import com.sca4d.core.spring.config.ConditionalOnCapability;
import com.sca4d.iot.api.IotCapabilities;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Objects;

/** 验证 EMQX 回调提交的共享 Secret，不处理具体回调内容。 */
@Component
@ConditionalOnCapability(IotCapabilities.TRANSPORT)
@ConditionalOnProperty(prefix = "sca4dj.iot.emqx", name = "enabled", havingValue = "true")
public final class EmqxCallbackAuthenticator {

    private static final Logger LOGGER = LoggerFactory.getLogger(EmqxCallbackAuthenticator.class);

    private final byte @NotNull [] expectedSecret;

    /**
     * 创建 EMQX 回调认证器。
     *
     * @param properties 具体应用的 EMQX webhook 配置
     * @throws IllegalArgumentException EMQX webhook Secret 缺失时抛出
     */
    public EmqxCallbackAuthenticator(@NotNull AppIotProperties properties) {
        this.expectedSecret = Objects.requireNonNull(properties, "IoT 应用配置不能为空")
                .emqxWebhookSecret()
                .getBytes(StandardCharsets.UTF_8);
    }

    /**
     * 使用固定时间字节比较验证 EMQX 共享 Secret。
     *
     * @param candidate EMQX 回调 Header 中的 Secret；Header 缺失时为 null
     * @throws ResponseStatusException Secret 缺失或不匹配时抛出 401
     */
    public void authenticate(@Nullable String candidate) {
        byte[] actual = candidate == null ? new byte[0] : candidate.getBytes(StandardCharsets.UTF_8);
        if (!MessageDigest.isEqual(expectedSecret, actual)) {
            LOGGER.warn(
                    "EMQX 回调认证失败，当前请求已拒绝，使用正确 Secret 后可重试 | header={}",
                    IotHttpHeaders.EMQX_SECRET);
            throw new ResponseStatusException(UNAUTHORIZED, "EMQX 回调认证失败");
        }
    }
}
