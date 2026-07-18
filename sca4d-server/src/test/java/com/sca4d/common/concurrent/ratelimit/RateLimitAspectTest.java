package com.sca4d.common.concurrent.ratelimit;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.sca4d.common.concurrent.SpelKeyEvaluator;
import com.sca4d.config.properties.Sca4djConcurrentProperties;
import jakarta.servlet.http.HttpServletRequest;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.reflect.MethodSignature;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.ObjectProvider;

import java.lang.reflect.Method;
import java.time.Duration;

/** 验证限流切面的规则选择、key 生成、令牌消费和拒绝结果。 */
class RateLimitAspectTest {

    @AfterEach
    void clearContext() {
        RateLimitContextHolder.clear();
    }

    @Test
    void shouldConsumeEveryDeclaredDimensionBeforeProceeding() throws Throwable {
        RateLimiter limiter = mock(RateLimiter.class);
        when(limiter.tryConsume(anyString(), any())).thenReturn(RateLimitResult.allowed(10));
        RateLimitAspect aspect = aspect(limiter);
        ProceedingJoinPoint joinPoint = joinPoint("valid");
        RateLimitContextHolder.set(new RateLimitContext("POST:/orders", "192.0.2.1", "JUnit", null));

        aspect.around(joinPoint);

        verify(limiter, Mockito.times(2)).tryConsume(anyString(), any());
        verify(joinPoint).proceed();
    }

    @Test
    void shouldConsumeAllDimensionsAndUseLongestRetryAfterWhenRejected() throws Throwable {
        RateLimiter limiter = mock(RateLimiter.class);
        when(limiter.tryConsume(anyString(), any()))
                .thenReturn(RateLimitResult.rejected(0, 1_000_000_000L))
                .thenReturn(RateLimitResult.rejected(0, 3_000_000_000L));
        RateLimitAspect aspect = aspect(limiter);
        ProceedingJoinPoint joinPoint = joinPoint("valid");
        RateLimitContextHolder.set(new RateLimitContext("POST:/orders", "192.0.2.1", "JUnit", null));

        assertThatThrownBy(() -> aspect.around(joinPoint))
                .isInstanceOf(RateLimitException.class)
                .extracting("retryAfterSeconds")
                .isEqualTo(3L);
        verify(limiter, Mockito.times(2)).tryConsume(anyString(), any());
        verify(joinPoint, never()).proceed();
    }

    @Test
    void shouldRejectEmptyOrDuplicateDimensionsBeforeBusinessExecution() throws Throwable {
        RateLimiter limiter = mock(RateLimiter.class);
        RateLimitAspect aspect = aspect(limiter);
        ProceedingJoinPoint empty = joinPoint("emptyTypes");
        ProceedingJoinPoint duplicate = joinPoint("duplicateTypes");

        assertThatThrownBy(() -> aspect.around(empty))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("@RateLimit 至少需要一个限流维度");
        assertThatThrownBy(() -> aspect.around(duplicate))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("@RateLimit 不能重复声明同一限流维度");
        verify(empty, never()).proceed();
        verify(duplicate, never()).proceed();
        verify(limiter, never()).tryConsume(anyString(), any());
    }

    @Test
    void shouldRejectZeroRuleOverrideBeforeBusinessExecution() throws Throwable {
        RateLimiter limiter = mock(RateLimiter.class);
        RateLimitAspect aspect = aspect(limiter);
        ProceedingJoinPoint joinPoint = joinPoint("zeroCapacity");

        assertThatThrownBy(() -> aspect.around(joinPoint))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("覆盖值必须大于 0");
        verify(joinPoint, never()).proceed();
    }

    @Test
    void shouldRejectInvalidGlobalRateLimitConfiguration() {
        Sca4djConcurrentProperties.RateLimit configuration = new Sca4djConcurrentProperties.RateLimit();

        assertThatThrownBy(() -> configuration.setKeyPrefix(" "))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("限流 key 前缀不能为空");
        assertThatThrownBy(() -> configuration.setCapacity(0))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("限流容量必须大于 0");
        assertThatThrownBy(() -> configuration.setRefillTokens(0))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("限流补充令牌数必须大于 0");
        assertThatThrownBy(() -> configuration.setTimeWindow(Duration.ZERO))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("限流窗口必须大于 0");
        assertThatThrownBy(() -> configuration.setKeepAfterRefill(Duration.ofSeconds(-1)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("限流状态保留时间不能为负数");
    }

    /**
     * 创建不依赖 Servlet 请求代理的限流切面测试实例。
     *
     * @param limiter 当前场景使用的令牌消费桩
     * @return 使用真实 key 生成和规则配置的切面
     */
    private RateLimitAspect aspect(RateLimiter limiter) {
        @SuppressWarnings("unchecked")
        ObjectProvider<HttpServletRequest> requestProvider = mock(ObjectProvider.class);
        Sca4djConcurrentProperties properties = new Sca4djConcurrentProperties();
        return new RateLimitAspect(
                limiter,
                new RateLimitKeyGenerator(properties),
                new SpelKeyEvaluator(),
                properties,
                requestProvider);
    }

    /**
     * 创建指向测试目标指定方法的 AOP 调用点。
     *
     * @param methodName RateLimitedTarget 中的方法名
     * @return 可验证业务方法是否执行的调用点
     */
    private ProceedingJoinPoint joinPoint(String methodName) throws NoSuchMethodException {
        Method method = RateLimitedTarget.class.getDeclaredMethod(methodName);
        MethodSignature signature = mock(MethodSignature.class);
        when(signature.getMethod()).thenReturn(method);
        ProceedingJoinPoint joinPoint = mock(ProceedingJoinPoint.class);
        when(joinPoint.getSignature()).thenReturn(signature);
        when(joinPoint.getTarget()).thenReturn(new RateLimitedTarget());
        when(joinPoint.getArgs()).thenReturn(new Object[0]);
        return joinPoint;
    }

    /** 声明不同 key 和规则的限流方法供切面反射调用。 */
    private static final class RateLimitedTarget {

        @RateLimit(types = {RateLimitType.IP, RateLimitType.USER})
        void valid() {
        }

        @RateLimit(types = {})
        void emptyTypes() {
        }

        @RateLimit(types = {RateLimitType.IP, RateLimitType.IP})
        void duplicateTypes() {
        }

        @RateLimit(capacity = 0)
        void zeroCapacity() {
        }
    }
}
