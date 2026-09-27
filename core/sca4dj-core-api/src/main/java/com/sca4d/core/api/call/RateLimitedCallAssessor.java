package com.sca4d.core.api.call;

import cn.hutool.crypto.digest.DigestUtil;
import com.sca4d.core.api.annotation.RateLimited;
import com.sca4d.core.api.audit.HttpCall;
import com.sca4d.core.api.concurrent.RateLimit;
import com.sca4d.core.api.concurrent.RateLimitDecision;
import com.sca4d.core.api.concurrent.RateLimitDimension;
import com.sca4d.core.api.concurrent.RateLimitExceededException;
import com.sca4d.core.api.concurrent.RateLimiter;
import com.sca4d.core.api.name.Key;
import com.sca4d.core.api.name.code.TreeCode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.Method;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/** 解析 `@RateLimited` 的内置和自定义维度，并取得每个维度的限流额度。 */
public final class RateLimitedCallAssessor {

    private static final Logger LOG = LoggerFactory.getLogger(RateLimitedCallAssessor.class);

    private final RateLimiter rateLimiter;

    private final CallValueResolverLookup resolverLookup;

    /**
     * 创建限流调用评估器。
     *
     * @param rateLimiter 多个应用实例共享的限流器
     * @param resolverLookup 自定义限流键解析器查找入口
     */
    public RateLimitedCallAssessor(RateLimiter rateLimiter, CallValueResolverLookup resolverLookup) {
        this.rateLimiter = Objects.requireNonNull(rateLimiter, "限流器不能为空");
        this.resolverLookup = Objects.requireNonNull(resolverLookup, "调用值解析器目录不能为空");
    }

    /**
     * 取得当前方法声明的全部限流额度。
     *
     * @param execution 当前方法执行
     * @throws RateLimitExceededException 任一维度没有剩余额度时抛出
     */
    public void assess(CallExecution execution) {
        CallExecution checked = Objects.requireNonNull(execution, "方法执行不能为空");
        RateLimited rateLimited = checked.rateLimited().orElse(null);
        if (rateLimited == null) {
            return;
        }

        // 每个维度使用独立令牌桶，全部维度判断完后按最长等待时间决定是否拒绝。
        List<String> keys = rateLimitKeys(rateLimited, checked);
        RateLimit limit = new RateLimit(
                rateLimited.permits(),
                Duration.ofSeconds(rateLimited.windowSeconds()));
        String scope = rateLimitScope(checked.declaringType(), checked.method());
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
    }

    /**
     * 把限流声明解析成需要分别取得额度的摘要键。
     *
     * @param rateLimited 当前限流声明
     * @param execution 当前方法执行
     * @return 至少包含一个且没有重复维度的键
     */
    private List<String> rateLimitKeys(RateLimited rateLimited, CallExecution execution) {
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

        if (rateLimited.keyResolver() != NoRateLimitKeyResolver.class) {
            Object resolved = resolverLookup.resolver(rateLimited.keyResolver()).resolve(execution);
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
    private static HttpCall requireHttpCall(Call call, RateLimitDimension dimension) {
        return call.httpCall()
                .orElseThrow(() -> new IllegalStateException(dimension + " 限流只适用于 HTTP 调用"));
    }

    /**
     * 使用摘要隐藏限流存储键中的原始维度值。
     *
     * @param dimension 维度名称
     * @param value 原始维度值
     * @return 可用于限流存储的摘要键
     */
    private static String rateLimitKey(String dimension, String value) {
        return dimension + "=" + DigestUtil.sha256Hex(value);
    }

    /**
     * 生成由具体类型和完整方法签名确定的限流用途。
     *
     * @param declaringType 具体业务类型
     * @param method 业务方法
     * @return 稳定限流用途
     */
    private static String rateLimitScope(Class<?> declaringType, Method method) {
        return "call." + DigestUtil.sha256Hex(methodSignature(declaringType, method));
    }

    /**
     * 返回由具体类型和完整参数类型组成的方法签名。
     *
     * @param declaringType 具体业务类型
     * @param method 业务方法
     * @return 稳定方法签名
     */
    static String methodSignature(Class<?> declaringType, Method method) {
        StringBuilder signature = new StringBuilder(declaringType.getName())
                .append('#')
                .append(method.getName())
                .append('(');
        Class<?>[] parameterTypes = method.getParameterTypes();
        for (int index = 0; index < parameterTypes.length; index++) {
            if (index > 0) {
                signature.append(',');
            }
            signature.append(parameterTypes[index].getName());
        }
        return signature.append(')').toString();
    }

}
