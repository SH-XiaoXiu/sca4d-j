package com.sca4d.gateway.registration;

import com.sca4d.core.api.gateway.GatewayHttpHeaders;
import com.sca4d.core.api.gateway.GatewayHttpPaths;
import com.sca4d.core.api.gateway.GatewayRegistrationRequest;
import com.sca4d.core.api.gateway.GatewayRegistrationResponse;
import com.sca4d.gateway.config.GatewayProperties;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

/** 提供 Server 实例注册、续租和带租约 ID 保护的注销控制 API。 */
@RestController
@RequestMapping(GatewayHttpPaths.REGISTRATIONS)
public class GatewayRegistrationController {

    private static final Logger LOGGER = LoggerFactory.getLogger(GatewayRegistrationController.class);

    private final @NotNull GatewayInstanceRegistry registry;

    private final byte @NotNull [] expectedSecret;

    /**
     * 创建注册控制器。
     *
     * @param registry 内存租约注册表
     * @param properties 已校验的 Gateway 配置
     */
    @SuppressFBWarnings(value = "EI_EXPOSE_REP2", justification = "registry 是注入的共享实例注册协作对象，控制 API 必须读写同一个注册表")
    public GatewayRegistrationController(
            @NotNull GatewayInstanceRegistry registry,
            @NotNull GatewayProperties properties) {
        this.registry = registry;
        this.expectedSecret = properties.validatedSecret().getBytes(StandardCharsets.UTF_8);
    }

    /** @return 新租约 ID；注册失败或 Secret 错误时不会修改注册表 */
    @PostMapping
    public @NotNull ResponseEntity<GatewayRegistrationResponse> register(
            @RequestHeader(name = GatewayHttpHeaders.SECRET, required = false) @Nullable String secret,
            @RequestBody @NotNull GatewayRegistrationRequest request) {
        verifySecret(secret);
        GatewayInstance instance = registry.register(request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(new GatewayRegistrationResponse(instance.leaseId()));
    }

    /** @return 当前租约续租成功时返回 204；旧租约返回 404 */
    @PutMapping(GatewayHttpPaths.REGISTRATION_LEASE_PATTERN)
    public @NotNull ResponseEntity<Void> renew(
            @RequestHeader(name = GatewayHttpHeaders.SECRET, required = false) @Nullable String secret,
            @PathVariable @NotNull String instanceId,
            @PathVariable @NotNull String leaseId) {
        verifySecret(secret);
        return registry.renew(instanceId, leaseId)
                ? ResponseEntity.noContent().build()
                : ResponseEntity.notFound().build();
    }

    /** @return 当前租约删除成功时返回 204；旧租约返回 404 */
    @DeleteMapping(GatewayHttpPaths.REGISTRATION_LEASE_PATTERN)
    public @NotNull ResponseEntity<Void> remove(
            @RequestHeader(name = GatewayHttpHeaders.SECRET, required = false) @Nullable String secret,
            @PathVariable @NotNull String instanceId,
            @PathVariable @NotNull String leaseId) {
        verifySecret(secret);
        return registry.remove(instanceId, leaseId)
                ? ResponseEntity.noContent().build()
                : ResponseEntity.notFound().build();
    }

    /** 使用固定时间字节比较验证控制 API Secret。 */
    private void verifySecret(@Nullable String candidate) {
        byte[] actual = candidate == null ? new byte[0] : candidate.getBytes(StandardCharsets.UTF_8);
        if (!MessageDigest.isEqual(expectedSecret, actual)) {
            LOGGER.warn(
                    "Gateway 注册控制认证失败，当前请求已拒绝，使用正确 Secret 后可重试 | header={}",
                    GatewayHttpHeaders.SECRET);
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Gateway 注册认证失败");
        }
    }

    /** @return 注册资料格式不合法时使用的空 400 响应 */
    @ExceptionHandler(IllegalArgumentException.class)
    public @NotNull ResponseEntity<Void> invalidRegistration() {
        return ResponseEntity.badRequest().build();
    }
}
