package com.sca4d.common.concurrent.ratelimit;

import cn.hutool.crypto.digest.DigestUtil;
import com.sca4d.common.auth.Principal;
import com.sca4d.common.auth.Subject;
import com.sca4d.common.request.CurrentRequest;
import com.sca4d.common.request.CurrentRequestHolder;
import com.sca4d.config.properties.Sca4djConcurrentProperties;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.springframework.util.StringUtils;

/**
 * 限流 key 生成器。
 */
public class RateLimitKeyGenerator {

    private final Sca4djConcurrentProperties properties;

    /**
     * 创建不在 Redis key 中暴露原始限流维度值的 key 生成器。
     *
     * @param properties 应用和环境级限流 key 前缀配置
     */
    public RateLimitKeyGenerator(@NotNull Sca4djConcurrentProperties properties) {
        this.properties = properties;
    }

    /**
     * 生成不暴露原始维度值且长度有界的 Redis 限流 key。
     *
     * @param type GLOBAL、IP、UA、USER、CONNECTION 或 CUSTOM 维度
     * @param context 当前协议入口采集的固定请求数据
     * @param customKey CUSTOM 维度的 SpEL 结果，其他维度忽略
     * @return 前缀、维度、主体摘要和 endpoint 摘要组成的 Redis key
     */
    public @NotNull String generate(
            @NotNull RateLimitType type, @NotNull RateLimitContext context, @Nullable String customKey) {
        String prefix = properties.getRateLimit().getKeyPrefix();
        String endpoint = DigestUtil.sha256Hex(context.getEndpoint());
        return switch (type) {
            case GLOBAL -> prefix + ":global:" + endpoint;
            case IP -> prefix + ":ip:" + DigestUtil.sha256Hex(context.getClientIp()) + ":" + endpoint;
            case UA -> prefix + ":ua:" + DigestUtil.sha256Hex(context.getUserAgent()) + ":" + endpoint;
            case USER -> prefix + ":user:" + DigestUtil.sha256Hex(userKey()) + ":" + endpoint;
            case CONNECTION -> prefix + ":connection:" + DigestUtil.sha256Hex(context.getConnectionId()) + ":" + endpoint;
            case CUSTOM -> prefix + ":custom:" + requiredCustomKey(customKey) + ":" + endpoint;
        };
    }

    /**
     * 按 Principal、Subject、匿名的优先级选择 USER 维度主体。
     *
     * @return 当前执行 Principal 或认证 Subject 的类型化标识，没有请求身份时为 anonymous
     */
    private @NotNull String userKey() {
        CurrentRequest auth = CurrentRequestHolder.currentOrNull();
        if (auth == null) {
            return "anonymous";
        }
        Principal principal = auth.getPrincipal();
        if (principal != null) {
            return "principal:" + principal.requirePrincipalId();
        }
        Subject subject = auth.getSubject();
        if (subject != null) {
            return "subject:" + subject.getSubjectId();
        }
        return "anonymous";
    }

    /**
     * 校验并摘要 CUSTOM 维度的业务 key。
     *
     * @param customKey 限流声明 SpEL 计算结果
     * @return 保持原值大小写差异的 SHA-256 摘要
     * @throws IllegalArgumentException CUSTOM 维度未产生有效文本时抛出
     */
    private @NotNull String requiredCustomKey(@Nullable String customKey) {
        if (!StringUtils.hasText(customKey)) {
            throw new IllegalArgumentException("CUSTOM 限流必须声明 key");
        }
        return DigestUtil.sha256Hex(customKey.trim());
    }
}
