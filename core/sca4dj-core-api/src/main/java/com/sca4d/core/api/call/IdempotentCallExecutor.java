package com.sca4d.core.api.call;

import cn.hutool.crypto.digest.DigestUtil;
import com.sca4d.core.api.annotation.Idempotent;
import com.sca4d.core.api.concurrent.IdempotencyBeginResult;
import com.sca4d.core.api.concurrent.IdempotencyBeginStatus;
import com.sca4d.core.api.concurrent.IdempotencyFingerprints;
import com.sca4d.core.api.concurrent.IdempotencyInProgressException;
import com.sca4d.core.api.concurrent.IdempotencyKey;
import com.sca4d.core.api.concurrent.IdempotencyKeyConflictException;
import com.sca4d.core.api.concurrent.IdempotencyStore;
import com.sca4d.core.api.utils.DurationUtils;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.util.Objects;
import java.util.concurrent.Callable;

/** 执行 `@Idempotent` 的处理权取得、结果重放、成功保存和失败释放。 */
public final class IdempotentCallExecutor {

    private static final Logger LOG = LoggerFactory.getLogger(IdempotentCallExecutor.class);

    private final IdempotencyStore idempotencyStore;

    private final CallValueResolverLookup resolverLookup;

    private final IdempotencyFingerprints fingerprints;

    private final Duration processingTtl;

    private final Duration retryAfter;

    /**
     * 创建幂等调用执行器。
     *
     * @param idempotencyStore 幂等处理中状态和成功结果存储
     * @param resolverLookup 幂等键解析器查找入口
     * @param fingerprints 方法实参规范化和摘要入口
     * @param processingTtl 处理中记录的租约
     * @param retryAfter 仍在处理中时建议等待的时间
     */
    public IdempotentCallExecutor(
            IdempotencyStore idempotencyStore,
            CallValueResolverLookup resolverLookup,
            IdempotencyFingerprints fingerprints,
            Duration processingTtl,
            Duration retryAfter) {
        this.idempotencyStore = Objects.requireNonNull(idempotencyStore, "幂等结果存储不能为空");
        this.resolverLookup = Objects.requireNonNull(resolverLookup, "调用值解析器目录不能为空");
        this.fingerprints = Objects.requireNonNull(fingerprints, "幂等参数指纹入口不能为空");
        this.processingTtl = DurationUtils.requirePositive(processingTtl, "幂等处理租约");
        this.retryAfter = DurationUtils.requirePositive(retryAfter, "幂等建议重试时间");
    }

    /**
     * 按当前幂等声明执行下一阶段，或直接返回已经保存的结果。
     *
     * @param execution 当前方法执行
     * @param invocation 取得处理权后执行的锁和业务方法阶段
     * @param <T> 业务返回类型
     * @return 实际业务结果或已保存的结果
     * @throws Exception 下一阶段抛出的受检异常
     */
    public <T> @Nullable T execute(CallExecution execution, Callable<T> invocation) throws Exception {
        CallExecution checked = Objects.requireNonNull(execution, "方法执行不能为空");
        Callable<T> checkedInvocation = Objects.requireNonNull(invocation, "下一调用阶段不能为空");
        Idempotent idempotent = checked.idempotent().orElse(null);
        if (idempotent == null) {
            return checkedInvocation.call();
        }

        Object resolvedKey = resolverLookup.resolver(idempotent.keyResolver()).resolve(checked);
        if (!(resolvedKey instanceof IdempotencyKey clientKey)) {
            throw new IllegalStateException(
                    "调用值解析器返回类型不匹配: "
                            + idempotent.keyResolver().getName()
                            + "，要求 "
                            + IdempotencyKey.class.getName());
        }
        IdempotencyKey key = scopedKey(checked, clientKey);
        Duration retention = DurationUtils.requirePositive(
                Duration.ofSeconds(idempotent.retentionSeconds()),
                "幂等结果保留时间");
        String fingerprint = fingerprint(checked);
        IdempotencyBeginResult<T> begin = idempotencyStore.begin(
                key,
                fingerprint,
                checked.resultType(),
                processingTtl,
                retryAfter);
        if (begin.status() == IdempotencyBeginStatus.REPLAY) {
            T result = begin.replayResult();
            checked.replay(result);
            LOG.trace(
                    "调用复用幂等结果 | callId={} method={}",
                    checked.call().callId(),
                    checked.method().getName());
            return result;
        }
        if (begin.status() == IdempotencyBeginStatus.IN_PROGRESS) {
            throw new IdempotencyInProgressException(begin.retryAfter());
        }
        if (begin.status() == IdempotencyBeginStatus.CONFLICT) {
            throw new IdempotencyKeyConflictException();
        }
        String token = Objects.requireNonNull(begin.token(), "幂等 ACQUIRED 结果缺少所有权 token");
        LOG.trace(
                "调用已取得幂等处理权 | callId={} method={}",
                checked.call().callId(),
                checked.method().getName());

        T result;
        try {
            result = checkedInvocation.call();
        } catch (Exception failure) {
            release(key, token, failure, checked);
            throw failure;
        }

        boolean stored = idempotencyStore.succeed(
                key,
                token,
                fingerprint,
                result,
                checked.resultType(),
                retention);
        if (!stored) {
            throw new IllegalStateException("业务调用已完成，但当前请求未能保存幂等结果");
        }
        LOG.trace(
                "调用已保存幂等结果 | callId={} method={}",
                checked.call().callId(),
                checked.method().getName());
        return result;
    }

    /**
     * 业务调用失败后只释放仍属于当前 token 的处理中记录。
     *
     * @param key 当前方法范围内的幂等键
     * @param token 当前请求取得的所有权 token
     * @param failure 导致当前调用失败的异常
     * @param execution 当前方法执行
     */
    private void release(
            IdempotencyKey key,
            String token,
            Exception failure,
            CallExecution execution) {
        try {
            if (!idempotencyStore.release(key, token)) {
                LOG.warn(
                        "幂等处理中记录未由当前请求释放，将等待自动过期 | callId={} method={}",
                        execution.call().callId(),
                        execution.method().getName());
            }
        } catch (RuntimeException releaseFailure) {
            failure.addSuppressed(releaseFailure);
            LOG.error(
                    "幂等处理中记录释放失败，将等待自动过期 | callId={} method={}",
                    execution.call().callId(),
                    execution.method().getName(),
                    releaseFailure);
        }
    }

    /**
     * 让同一客户端键在不同业务方法中使用互不冲突的存储记录。
     *
     * @param execution 当前方法执行
     * @param key 解析器返回的客户端幂等键
     * @return 包含方法范围的幂等键
     */
    private static IdempotencyKey scopedKey(CallExecution execution, IdempotencyKey key) {
        return new IdempotencyKey(
                "call."
                        + DigestUtil.sha256Hex(
                                RateLimitedCallAssessor.methodSignature(
                                        execution.declaringType(),
                                        execution.method()))
                        + ":"
                        + DigestUtil.sha256Hex(key.value()));
    }

    /**
     * 使用方法签名和字段顺序稳定的参数 JSON 生成请求指纹。
     *
     * @param execution 当前方法执行
     * @return 当前方法实参的稳定摘要
     */
    private String fingerprint(CallExecution execution) {
        return DigestUtil.sha256Hex(
                RateLimitedCallAssessor.methodSignature(execution.declaringType(), execution.method())
                        + ":"
                        + fingerprints.canonicalArguments(execution.arguments()));
    }

}
