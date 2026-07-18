package com.sca4d.common.concurrent.ratelimit;

import com.sca4d.common.concurrent.SpelKeyEvaluator;
import com.sca4d.common.exception.CommonErrorCode;
import com.sca4d.config.properties.Sca4djConcurrentProperties;
import jakarta.servlet.http.HttpServletRequest;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.aop.support.AopUtils;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.core.annotation.Order;

import java.lang.reflect.Method;
import java.time.Duration;
import java.util.EnumSet;
import java.util.List;

/**
 * 限流切面。
 */
@Aspect
@Order(200)
public class RateLimitAspect {

    private static final Logger log = LoggerFactory.getLogger(RateLimitAspect.class);

    private final RateLimiter rateLimiter;
    private final RateLimitKeyGenerator keyGenerator;
    private final SpelKeyEvaluator keyEvaluator;
    private final Sca4djConcurrentProperties properties;
    private final ObjectProvider<HttpServletRequest> requestProvider;

    /**
     * 创建读取限流注解、计算 key 并消费 Bucket4j 集群令牌的切面。
     *
     * @param rateLimiter 消费集群共享令牌桶的实现
     * @param keyGenerator 限流维度与请求数据 key 生成器
     * @param keyEvaluator CUSTOM 维度使用的共用 SpEL 解析器
     * @param properties 全局限流开关、默认规则和 key 前缀
     * @param requestProvider HTTP 调用场景的可选 Servlet 请求来源
     */
    public RateLimitAspect(
            @NotNull RateLimiter rateLimiter,
            @NotNull RateLimitKeyGenerator keyGenerator,
            @NotNull SpelKeyEvaluator keyEvaluator,
            @NotNull Sca4djConcurrentProperties properties,
            @NotNull ObjectProvider<HttpServletRequest> requestProvider) {
        this.rateLimiter = rateLimiter;
        this.keyGenerator = keyGenerator;
        this.keyEvaluator = keyEvaluator;
        this.properties = properties;
        this.requestProvider = requestProvider;
    }

    /**
     * 在声明了限流的类或方法外包裹限流逻辑。
     *
     * @param joinPoint 切点
     * @return 原方法返回值
     * @throws Throwable 原方法或限流异常
     */
    @Around("@annotation(com.sca4d.common.concurrent.ratelimit.RateLimit)"
            + " || @within(com.sca4d.common.concurrent.ratelimit.RateLimit)")
    public Object around(@NotNull ProceedingJoinPoint joinPoint) throws Throwable {
        RateLimit rateLimit = findRateLimit(joinPoint);
        if (rateLimit == null || !properties.getRateLimit().isEnabled()) {
            return joinPoint.proceed();
        }
        if (rateLimit.types().length == 0) {
            throw new IllegalStateException("@RateLimit 至少需要一个限流维度");
        }
        EnumSet<RateLimitType> types = EnumSet.copyOf(List.of(rateLimit.types()));
        if (types.size() != rateLimit.types().length) {
            throw new IllegalStateException("@RateLimit 不能重复声明同一限流维度");
        }

        RateLimitRule rule = rule(rateLimit);
        RateLimitContext context = context(joinPoint);
        String customKey = keyEvaluator.evaluate(rateLimit.key(), joinPoint);
        long retryAfterSeconds = 0;
        for (RateLimitType type : types) {
            String key = keyGenerator.generate(type, context, customKey);
            RateLimitResult result = rateLimiter.tryConsume(key, rule);
            if (!result.isAllowed()) {
                retryAfterSeconds = Math.max(retryAfterSeconds, result.getRetryAfterSeconds());
            }
        }
        if (retryAfterSeconds > 0) {
            log.trace(
                    "限流规则拒绝当前请求 | dimensions={} retryAfterSeconds={}",
                    types,
                    retryAfterSeconds);
            throw new RateLimitException(CommonErrorCode.TOO_MANY_REQUESTS, retryAfterSeconds);
        }
        log.trace("限流规则允许当前请求 | dimensions={}", types);
        return joinPoint.proceed();
    }

    /**
     * 将注解覆盖值与全局默认值合成为完整令牌桶规则。
     *
     * @param rateLimit 当前限流声明
     * @return 经过正数校验的容量、补充令牌和窗口规则
     * @throws IllegalStateException 注解显式使用零覆盖值时抛出
     */
    private @NotNull RateLimitRule rule(@NotNull RateLimit rateLimit) {
        Sca4djConcurrentProperties.RateLimit defaults = properties.getRateLimit();
        if (rateLimit.capacity() == 0 || rateLimit.refillTokens() == 0 || rateLimit.timeWindowSeconds() == 0) {
            throw new IllegalStateException("@RateLimit 容量、补充令牌和窗口覆盖值必须大于 0 或使用负数继承全局配置");
        }
        long capacity = rateLimit.capacity() > 0 ? rateLimit.capacity() : defaults.getCapacity();
        long refillTokens = rateLimit.refillTokens() > 0 ? rateLimit.refillTokens() : defaults.getRefillTokens();
        Duration window = rateLimit.timeWindowSeconds() > 0
                ? Duration.ofSeconds(rateLimit.timeWindowSeconds())
                : defaults.getTimeWindow();
        return new RateLimitRule(capacity, refillTokens, window);
    }

    /**
     * 按最具体实现方法优先、目标类型其次的顺序读取合并限流声明。
     *
     * @param joinPoint 当前 AOP 调用点
     * @return 当前方法或类型的限流声明，没有声明时返回 null
     */
    private @Nullable RateLimit findRateLimit(@NotNull ProceedingJoinPoint joinPoint) {
        MethodSignature signature = (MethodSignature) joinPoint.getSignature();
        Method method = signature.getMethod();
        Object target = joinPoint.getTarget();
        Method specificMethod = target == null ? method : AopUtils.getMostSpecificMethod(method, target.getClass());
        RateLimit methodRateLimit = AnnotatedElementUtils.findMergedAnnotation(specificMethod, RateLimit.class);
        if (methodRateLimit != null) {
            return methodRateLimit;
        }
        Class<?> targetClass = target == null ? method.getDeclaringClass() : target.getClass();
        return AnnotatedElementUtils.findMergedAnnotation(targetClass, RateLimit.class);
    }

    /**
     * 按协议实现上下文、Servlet 请求、方法标识的顺序解析限流请求数据。
     *
     * <p>WebSocket 帧优先使用拦截器安装的上下文；HTTP 使用容器解析的客户端地址和最佳匹配路由模板；非请求线程回退到方法标识。
     *
     * @param joinPoint 当前 AOP 调用点
     * @return 不再引用可变请求对象的限流信息快照
     */
    private @NotNull RateLimitContext context(@NotNull ProceedingJoinPoint joinPoint) {
        RateLimitContext context = RateLimitContextHolder.currentOrNull();
        if (context != null) {
            return context;
        }
        HttpServletRequest request = requestProvider.getIfAvailable();
        if (request != null) {
            try {
                return RateLimitContext.http(request);
            } catch (IllegalStateException ex) {
                // 非 HTTP 线程中可能拿到请求代理，不能把该代理当作真实请求读取。
            }
        }
        MethodSignature signature = (MethodSignature) joinPoint.getSignature();
        Method method = signature.getMethod();
        return RateLimitContext.method(method.getDeclaringClass().getName() + "#" + method.getName());
    }
}
