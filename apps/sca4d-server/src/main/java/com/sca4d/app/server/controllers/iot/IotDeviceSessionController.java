package com.sca4d.app.server.controllers.iot;

import com.sca4d.app.server.iot.DeviceSessionService;
import com.sca4d.app.server.iot.HmacCanonicalRequest;
import com.sca4d.app.server.iot.IotHttpHeaders;
import com.sca4d.app.server.iot.view.IotDeviceSessionView;
import com.sca4d.core.api.annotation.AnonymousAccess;
import com.sca4d.core.api.error.exception.BusinessException;
import com.sca4d.core.spring.config.ConditionalOnCapability;
import com.sca4d.core.spring.gateway.TrustedGatewayRequest;
import com.sca4d.core.spring.web.ApiResponse;
import com.sca4d.iot.IotErrorCode;
import com.sca4d.iot.api.IotCapabilities;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.Objects;

/** 提供 Sca4d Server 示例应用真实 HTTP 请求的 Device MAC Session 接口。 */
@RestController
@AnonymousAccess
@ConditionalOnCapability(IotCapabilities.API)
@RequestMapping("/iot/device-sessions")
@Tag(name = "IoT Device Sessions")
public class IotDeviceSessionController {

    private static final int MAX_BODY_BYTES = 65_536;

    private final @NotNull DeviceSessionService sessions;

    /** 创建只负责 HTTP 输入转换的 Device Session Controller。 */
    public IotDeviceSessionController(@NotNull DeviceSessionService sessions) {
        this.sessions = Objects.requireNonNull(sessions, "Device Session 服务不能为空");
    }

    /** @return MAC 验证成功后签发的 MQTT Session Token、Session ID 和过期时间 */
    @PostMapping
    @Operation(summary = "使用 Device HMAC 建立 Session")
    public @NotNull ApiResponse<IotDeviceSessionView> establish(
            @RequestHeader(IotHttpHeaders.DEVICE_ID) @NotNull String deviceId,
            @RequestHeader(IotHttpHeaders.TIMESTAMP) @NotNull String timestamp,
            @RequestHeader(IotHttpHeaders.ONCE) @NotNull String nonce,
            @RequestHeader(IotHttpHeaders.SIGNATURE) @NotNull String signature,
            @RequestBody(required = false) byte @Nullable [] body,
            @NotNull HttpServletRequest request) {
        byte[] content = body == null ? new byte[0] : body;
        if (content.length > MAX_BODY_BYTES) {
            throw new BusinessException(IotErrorCode.DEVICE_AUTHENTICATION_FAILED);
        }
        HmacCanonicalRequest canonical = new HmacCanonicalRequest(
                positiveLong(deviceId),
                request.getMethod(),
                TrustedGatewayRequest.externalPath(request),
                canonicalQuery(request.getQueryString()),
                content,
                epochSecond(timestamp),
                nonce);
        return ApiResponse.ok(IotDeviceSessionView.from(sessions.establish(canonical, signature)));
    }

    /** 解析认证头中的正数 Device 标识。 */
    private static long positiveLong(@NotNull String value) {
        try {
            long parsed = Long.parseLong(value);
            if (parsed <= 0) {
                throw new NumberFormatException("Device 标识不是正数");
            }
            return parsed;
        } catch (NumberFormatException exception) {
            throw new BusinessException(IotErrorCode.DEVICE_AUTHENTICATION_FAILED, exception);
        }
    }

    /** 解析认证头中的 Unix 秒时间戳。 */
    private static @NotNull Instant epochSecond(@NotNull String value) {
        try {
            return Instant.ofEpochSecond(Long.parseLong(value));
        } catch (RuntimeException exception) {
            throw new BusinessException(IotErrorCode.DEVICE_AUTHENTICATION_FAILED, exception);
        }
    }

    /** 当前 Session 路由没有查询参数，出现 query 时拒绝产生两种 canonical 解释。 */
    private static @NotNull String canonicalQuery(@Nullable String query) {
        if (query == null || query.isEmpty()) {
            return "";
        }
        throw new BusinessException(IotErrorCode.DEVICE_AUTHENTICATION_FAILED);
    }
}
