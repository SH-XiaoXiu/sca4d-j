package com.sca4d.common.concurrent.lock;

import com.sca4d.common.concurrent.SpelKeyEvaluator;
import com.sca4d.config.properties.Sca4djConcurrentProperties;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.springframework.aop.support.AopUtils;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.core.annotation.Order;
import org.springframework.util.StringUtils;

import java.lang.reflect.Method;
import java.time.Duration;

/**
 * 分布式锁切面。
 */
@Aspect
@Order(100)
public class DistributedLockAspect {

    private final DistributedLockTemplate lockTemplate;
    private final SpelKeyEvaluator keyEvaluator;
    private final Sca4djConcurrentProperties properties;

    /**
     * 创建读取锁注解、计算 key 并调用集群锁模板的切面。
     *
     * @param lockTemplate 按等待时间和租约执行受保护代码的锁模板
     * @param keyEvaluator 锁 key 使用的共用 SpEL 解析器
     * @param properties 全局锁开关、前缀和默认时长配置
     */
    public DistributedLockAspect(
            @NotNull DistributedLockTemplate lockTemplate,
            @NotNull SpelKeyEvaluator keyEvaluator,
            @NotNull Sca4djConcurrentProperties properties) {
        this.lockTemplate = lockTemplate;
        this.keyEvaluator = keyEvaluator;
        this.properties = properties;
    }

    /**
     * 在声明了分布式锁的类或方法外包裹集群互斥逻辑。
     *
     * @param joinPoint 提供注解表达式参数和业务执行入口的 AOP 调用点
     * @return 获得锁后的原业务返回值
     * @throws Throwable 未获得锁、线程中断或原业务执行失败时抛出
     */
    @Around("@annotation(com.sca4d.common.concurrent.lock.DistributedLock)"
            + " || @within(com.sca4d.common.concurrent.lock.DistributedLock)")
    public Object around(@NotNull ProceedingJoinPoint joinPoint) throws Throwable {
        DistributedLock lock = findLock(joinPoint);
        if (lock == null || !properties.getLock().isEnabled()) {
            return joinPoint.proceed();
        }
        String key = lockKey(lock, joinPoint);
        Duration waitTime = duration(lock.waitTimeSeconds(), properties.getLock().getWaitTime());
        Duration leaseTime = duration(lock.leaseTimeSeconds(), properties.getLock().getLeaseTime());
        return lockTemplate.execute(key, waitTime, leaseTime, joinPoint::proceed);
    }

    /**
     * 计算带应用前缀的 Redisson 锁名称。
     *
     * @param lock 当前方法或类型的锁声明
     * @param joinPoint 提供 SpEL 参数变量的 AOP 调用点
     * @return 全局配置前缀与业务资源 key 组成的锁名称
     * @throws IllegalArgumentException 表达式没有产生有效文本时抛出
     */
    private @NotNull String lockKey(@NotNull DistributedLock lock, @NotNull ProceedingJoinPoint joinPoint) {
        String value = keyEvaluator.evaluate(lock.key(), joinPoint);
        if (!StringUtils.hasText(value)) {
            throw new IllegalArgumentException("分布式锁 key 不能为空");
        }
        return properties.getLock().getKeyPrefix() + ":" + value.trim();
    }

    /**
     * 解析注解秒数覆盖值。
     *
     * @param seconds 注解声明值，负数表示继承全局配置
     * @param fallback 当前全局等待或租约配置
     * @return 注解非负时对应秒数，否则返回全局配置
     */
    private @NotNull Duration duration(long seconds, @NotNull Duration fallback) {
        if (seconds < 0) {
            return fallback;
        }
        return Duration.ofSeconds(seconds);
    }

    /**
     * 按最具体实现方法优先、目标类型其次的顺序读取合并锁声明。
     *
     * @param joinPoint 当前 AOP 调用点
     * @return 当前方法或类型的锁声明，没有声明时返回 null
     */
    private @Nullable DistributedLock findLock(@NotNull ProceedingJoinPoint joinPoint) {
        MethodSignature signature = (MethodSignature) joinPoint.getSignature();
        Method method = signature.getMethod();
        Object target = joinPoint.getTarget();
        Method specificMethod = target == null ? method : AopUtils.getMostSpecificMethod(method, target.getClass());
        DistributedLock methodLock = AnnotatedElementUtils.findMergedAnnotation(specificMethod, DistributedLock.class);
        if (methodLock != null) {
            return methodLock;
        }
        Class<?> targetClass = target == null ? method.getDeclaringClass() : target.getClass();
        return AnnotatedElementUtils.findMergedAnnotation(targetClass, DistributedLock.class);
    }
}
