package com.sca4d.core.api.concurrent;

import cn.hutool.crypto.digest.DigestUtil;
import com.sca4d.core.api.annotation.RateLimited;
import com.sca4d.core.api.audit.HttpCall;
import com.sca4d.core.api.call.Call;
import com.sca4d.core.api.call.CallExecution;
import com.sca4d.core.api.call.CallLifecycle;
import com.sca4d.core.api.call.CallOutcome;
import com.sca4d.core.api.call.CallValueResolverLookup;
import com.sca4d.core.api.call.SPICallValueResolver;
import com.sca4d.core.api.name.Key;
import com.sca4d.core.api.name.code.TreeCode;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/** 解析 `@RateLimited` 的内置和自定义维度，并取得每个维度的限流额度。 */
public class RateLimitCallInterceptor implements CallLifecycle.Around {

    private static final Logger LOG = LoggerFactory.getLogger(RateLimitCallInterceptor.class);

    private final RateLimiter rateLimiter;

    private final CallValueResolverLookup resolverLookup;

    private final SPICallValueResolver expressionResolver;

    /**
     * 创建限流调用评估器。
     *
     * @param rateLimiter 多个应用实例共享的限流器
     * @param resolverLookup 自定义限流键解析器查找入口
     */
    public RateLimitCallInterceptor(@NotNull RateLimiter rateLimiter, @NotNull CallValueResolverLookup resolverLookup,
                                    @NotNull SPICallValueResolver expressionResolver) {
        this.rateLimiter = Objects.requireNonNull(rateLimiter, "限流器不能为空");
        this.resolverLookup = Objects.requireNonNull(resolverLookup, "调用值解析器目录不能为空");
        this.expressionResolver = Objects.requireNonNull(expressionResolver, "注解表达式解析器不能为空");
    }

    @Override
    public @NotNull TreeCode code() {
        return CallLifecycle.RATE_LIMIT;
    }

    @Override
    public boolean supports(@NotNull CallExecution execution) {
        return execution.declaration(RateLimited.class).isPresent();
    }

    /**
     * 取得当前方法声明的全部限流额度。
     *
     * @param execution 当前方法执行
     * @param next 取得全部额度后继续的调用
     * @return 内层调用结果
     * @throws RateLimitExceededException 任一维度没有剩余额度时抛出
     */
    @Override
    public @NotNull CallOutcome around(@NotNull CallExecution execution, @NotNull CallLifecycle.Next next) {
        CallExecution checked = Objects.requireNonNull(execution, "方法执行不能为空");
        RateLimited rateLimited = checked.declaration(RateLimited.class).orElseThrow();

        // 每个维度使用独立令牌桶，全部维度判断完后按最长等待时间决定是否拒绝。
        List<String> keys = rateLimitKeys(rateLimited, checked);
        RateLimit limit = new RateLimit(
                rateLimited.permits(),
                Duration.ofSeconds(rateLimited.windowSeconds()));
        String scope = "call." + DigestUtil.sha256Hex(checked.methodSignature());
        Duration retryAfter = Duration.ZERO;
        for (String key : keys) {
            RateLimitDecision decision = rateLimiter.tryAcquire(Key.of(TreeCode.of("RATE_LIMIT"), scope, key), limit);
            if (!decision.isAllowed() && decision.retryAfter().compareTo(retryAfter) > 0) {
                retryAfter = decision.retryAfter();
            }
        }
        LOG.trace(
                "调用已判断限流额度 | callId={} method={} dimensions={} allowed={}",
                checked.call().callId(),
                checked.method().getName(),
                keys.size(),
                retryAfter.isZero());
        if (!retryAfter.isZero()) {
            throw new RateLimitExceededException(retryAfter);
        }
        return next.proceed();
    }

    /**
     * 把限流声明解析成需要分别取得额度的摘要键。
     *
     * @param rateLimited 当前限流声明
     * @param execution 当前方法执行
     * @return 至少包含一个且没有重复维度的键
     */
    private @NotNull List<@NotNull String> rateLimitKeys(
            @NotNull RateLimited rateLimited,
            @NotNull CallExecution execution) {
        List<RateLimitDimension> declaredDimensions = List.of(rateLimited.dimensions());
        Set<RateLimitDimension> distinctDimensions = new LinkedHashSet<>(declaredDimensions);
        if (distinctDimensions.size() != declaredDimensions.size()) {
            throw new IllegalStateException("限流声明不能重复使用同一内置维度");
        }

        List<String> keys = new ArrayList<>(distinctDimensions.size() + 1);
        for (RateLimitDimension dimension : distinctDimensions) {
            keys.add(switch (dimension) {
                case GLOBAL -> "global";
                case IP -> rateLimitKey("ip", requireHttpCall(execution.call(), dimension).clientIp());
                case UA -> rateLimitKey(
                        "ua",
                        requireHttpCall(execution.call(), dimension)
                                .userAgent()
                                .map(value -> "present:" + value)
                                .orElse("absent"));
                case SUBJECT -> rateLimitKey(
                        "subject",
                        Long.toString(
                                execution.call()
                                        .subject()
                                        .orElseThrow(() -> new IllegalStateException("SUBJECT 限流要求当前调用存在 Subject"))
                                        .subjectId()));
                case ACCOUNT -> rateLimitKey(
                        "account",
                        Long.toString(
                                execution.call()
                                        .account()
                                        .orElseThrow(() -> new IllegalStateException("ACCOUNT 限流要求当前调用存在 Account"))
                                        .accountId()));
            });
        }

        boolean expression = !rateLimited.key().isBlank();
        boolean customResolver = rateLimited.keyResolver() != NoRateLimitKeyResolver.class;
        if (expression && customResolver) {
            throw new IllegalStateException("限流 key 与 keyResolver 必须二选一");
        }
        if (expression || customResolver) {
            Object resolved = expression
                    ? expressionResolver.resolve(rateLimited.key(), execution)
                    : resolverLookup.resolver(rateLimited.keyResolver()).resolve(execution);
            if (!(resolved instanceof String customKey)) {
                throw new IllegalStateException(
                        "调用值解析器返回类型不匹配: "
                                + rateLimited.keyResolver().getName()
                                + "，要求 "
                                + String.class.getName());
            }
            if (customKey.isBlank()) {
                throw new IllegalStateException(
                        "限流键解析器不能返回空白文本: "
                                + rateLimited.keyResolver().getName());
            }
            keys.add(rateLimitKey("custom", customKey));
        }
        if (keys.isEmpty()) {
            throw new IllegalStateException("限流声明至少需要一个内置维度或自定义键解析器");
        }
        return List.copyOf(keys);
    }

    /**
     * 取得 IP 或 UA 维度需要的 HTTP 请求快照。
     *
     * @param call 当前调用
     * @param dimension 当前 HTTP 限流维度
     * @return 当前 HTTP 请求快照
     */
    private static @NotNull HttpCall requireHttpCall(@NotNull Call call, @NotNull RateLimitDimension dimension) {
        return call.input(HttpCall.KEY)
                .orElseThrow(() -> new IllegalStateException(dimension + " 限流只适用于 HTTP 调用"));
    }

    /**
     * 使用摘要隐藏限流存储键中的原始维度值。
     *
     * @param dimension 维度名称
     * @param value 原始维度值
     * @return 可用于限流存储的摘要键
     */
    private static @NotNull String rateLimitKey(@NotNull String dimension, @NotNull String value) {
        return dimension + "=" + DigestUtil.sha256Hex(value);
    }

}
