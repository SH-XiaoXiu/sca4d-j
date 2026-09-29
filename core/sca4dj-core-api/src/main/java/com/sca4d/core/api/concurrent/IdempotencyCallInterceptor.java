package com.sca4d.core.api.concurrent;

import cn.hutool.crypto.digest.DigestUtil;
import com.sca4d.core.api.annotation.Idempotent;
import com.sca4d.core.api.call.CallExecution;
import com.sca4d.core.api.call.CallLifecycle;
import com.sca4d.core.api.call.CallOutcome;
import com.sca4d.core.api.call.CallValueResolverLookup;
import com.sca4d.core.api.call.NoCallValueResolver;
import com.sca4d.core.api.call.SPICallValueResolver;
import com.sca4d.core.api.name.code.TreeCode;
import com.sca4d.core.api.utils.DurationUtils;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.Future;

/** 执行 `@Idempotent` 的处理权取得、结果重放、成功保存和失败释放。 */
public class IdempotencyCallInterceptor implements CallLifecycle.Around {

    private static final Logger LOG = LoggerFactory.getLogger(IdempotencyCallInterceptor.class);

    /** 幂等处理权节点编码。 */
    public static final TreeCode CODE = TreeCode.of("CALL/IDEMPOTENCY");

    private final IdempotencyStore idempotencyStore;

    private final CallValueResolverLookup resolverLookup;

    private final SPICallValueResolver expressionResolver;

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
    public IdempotencyCallInterceptor(
            @NotNull IdempotencyStore idempotencyStore,
            @NotNull CallValueResolverLookup resolverLookup,
            @NotNull SPICallValueResolver expressionResolver,
            @NotNull IdempotencyFingerprints fingerprints,
            @NotNull Duration processingTtl,
            @NotNull Duration retryAfter) {
        this.idempotencyStore = Objects.requireNonNull(idempotencyStore, "幂等结果存储不能为空");
        this.resolverLookup = Objects.requireNonNull(resolverLookup, "调用值解析器目录不能为空");
        this.expressionResolver = Objects.requireNonNull(expressionResolver, "注解表达式解析器不能为空");
        this.fingerprints = Objects.requireNonNull(fingerprints, "幂等参数指纹入口不能为空");
        this.processingTtl = DurationUtils.requirePositive(processingTtl, "幂等处理租约");
        this.retryAfter = DurationUtils.requirePositive(retryAfter, "幂等建议重试时间");
    }

    @Override
    public @NotNull TreeCode code() {
        return CODE;
    }

    @Override
    public boolean supports(@NotNull CallExecution execution) {
        return execution.declaration(Idempotent.class).isPresent();
    }

    /** Future 不能作为幂等重放结果；异步提交应返回持久任务标识或在工作方法声明幂等。 */
    @Override
    public @NotNull CallOutcome around(@NotNull CallExecution execution, @NotNull CallLifecycle.Next next) {
        CallExecution checked = Objects.requireNonNull(execution, "方法执行不能为空");
        Idempotent idempotent = checked.declaration(Idempotent.class).orElseThrow();
        Class<?> returnType = checked.method().getReturnType();
        if (Future.class.isAssignableFrom(returnType) || CompletionStage.class.isAssignableFrom(returnType)) {
            throw new IllegalStateException("幂等方法不能返回 Future 或 CompletionStage");
        }

        // 在执行方法之前取得处理权；只有相同方法、参数和客户端键才允许重放。
        boolean expression = !idempotent.key().isBlank();
        boolean customResolver = idempotent.keyResolver() != NoCallValueResolver.class;
        if (expression && customResolver) {
            throw new IllegalStateException("幂等 key 与 keyResolver 必须二选一");
        }
        Object resolvedKey;
        if (expression) {
            resolvedKey = expressionResolver.resolve(idempotent.key(), checked);
        } else if (customResolver) {
            resolvedKey = resolverLookup.resolver(idempotent.keyResolver()).resolve(checked);
        } else {
            resolvedKey = checked.call()
                    .input(IdempotencyKey.INPUT)
                    .orElseThrow(() -> new IllegalStateException("当前调用没有提供幂等键"));
        }
        IdempotencyKey clientKey = normalizeKey(resolvedKey);
        IdempotencyKey key = scopedKey(checked, clientKey);
        Duration retention = DurationUtils.requirePositive(
                Duration.ofSeconds(idempotent.retentionSeconds()),
                "幂等结果保留时间");
        String fingerprint = fingerprint(checked);
        IdempotencyBeginResult<?> begin = idempotencyStore.begin(
                key,
                fingerprint,
                checked.resultType(),
                processingTtl,
                retryAfter);
        if (begin.status() == IdempotencyBeginStatus.REPLAY) {
            LOG.trace(
                    "调用复用幂等结果 | callId={} method={}",
                    checked.call().callId(),
                    checked.method().getName());
            return CallOutcome.replayed(begin.replayResult());
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

        CallOutcome outcome;
        try {
            outcome = next.proceed();
        } catch (RuntimeException | Error failure) {
            release(key, token, failure, checked);
            throw failure;
        }
        if (outcome.error() != null || outcome.completionFailure() != null) {
            release(
                    key,
                    token,
                    Objects.requireNonNull(
                            outcome.error() == null ? outcome.completionFailure() : outcome.error()),
                    checked);
            return outcome;
        }
        if (outcome.result() instanceof Future<?> || outcome.result() instanceof CompletionStage<?>) {
            throw new IllegalStateException("业务调用已完成，但 Future 或 CompletionStage 不能保存为幂等结果");
        }

        // 内层成功要求通过后保存结果，保存失败由生命周期保留为完成失败。
        boolean stored = idempotencyStore.succeed(
                key,
                token,
                fingerprint,
                outcome.result(),
                checked.resultType(),
                retention);
        if (!stored) {
            throw new IllegalStateException("业务调用已完成，但当前请求未能保存幂等结果");
        }
        LOG.trace(
                "调用已保存幂等结果 | callId={} method={}",
                checked.call().callId(),
                checked.method().getName());
        return outcome;
    }

    /** 异步持有幂等处理权直到内层 CompletionStage 最终完成。 */
    @Override
    public @NotNull CompletionStage<CallOutcome> aroundAsync(
            @NotNull CallExecution execution,
            @NotNull CallLifecycle.AsyncNext next) {
        CallExecution checked = Objects.requireNonNull(execution, "方法执行不能为空");
        Idempotent idempotent = checked.declaration(Idempotent.class).orElseThrow();
        IdempotencyKey key = scopedKey(checked, resolveKey(idempotent, checked));
        Duration retention = DurationUtils.requirePositive(
                Duration.ofSeconds(idempotent.retentionSeconds()),
                "幂等结果保留时间");
        String fingerprint = fingerprint(checked);
        IdempotencyBeginResult<?> begin = idempotencyStore.begin(
                key,
                fingerprint,
                checked.resultType(),
                processingTtl,
                retryAfter);
        if (begin.status() == IdempotencyBeginStatus.REPLAY) {
            return CompletableFuture.completedFuture(CallOutcome.replayed(begin.replayResult()));
        }
        if (begin.status() == IdempotencyBeginStatus.IN_PROGRESS) {
            throw new IdempotencyInProgressException(begin.retryAfter());
        }
        if (begin.status() == IdempotencyBeginStatus.CONFLICT) {
            throw new IdempotencyKeyConflictException();
        }
        String token = Objects.requireNonNull(begin.token(), "幂等 ACQUIRED 结果缺少所有权 token");
        return next.proceedAsync().handle((outcome, failure) -> {
            if (failure != null) {
                Throwable cause = failure instanceof java.util.concurrent.CompletionException
                        ? failure.getCause()
                        : failure;
                release(key, token, cause, checked);
                return cause instanceof com.sca4d.core.api.error.BusinessException
                        ? CallOutcome.denied(cause)
                        : CallOutcome.failed(cause);
            }
            if (outcome.error() != null || outcome.completionFailure() != null) {
                Throwable cause = outcome.error() == null ? outcome.completionFailure() : outcome.error();
                release(key, token, Objects.requireNonNull(cause), checked);
                return outcome;
            }
            try {
                if (!idempotencyStore.succeed(
                        key,
                        token,
                        fingerprint,
                        outcome.result(),
                        checked.resultType(),
                        retention)) {
                    return outcome.withCompletionFailure(
                            new IllegalStateException("业务调用已完成，但当前请求未能保存幂等结果"));
                }
                return outcome;
            } catch (RuntimeException failureToStore) {
                return outcome.withCompletionFailure(failureToStore);
            }
        });
    }

    private @NotNull IdempotencyKey resolveKey(
            @NotNull Idempotent idempotent,
            @NotNull CallExecution execution) {
        boolean expression = !idempotent.key().isBlank();
        boolean customResolver = idempotent.keyResolver() != NoCallValueResolver.class;
        if (expression && customResolver) {
            throw new IllegalStateException("幂等 key 与 keyResolver 必须二选一");
        }
        Object resolved = expression
                ? expressionResolver.resolve(idempotent.key(), execution)
                : customResolver
                        ? resolverLookup.resolver(idempotent.keyResolver()).resolve(execution)
                        : execution.call()
                                .input(IdempotencyKey.INPUT)
                                .orElseThrow(() -> new IllegalStateException("当前调用没有提供幂等键"));
        return normalizeKey(resolved);
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
            @NotNull IdempotencyKey key,
            @NotNull String token,
            @NotNull Throwable failure,
            @NotNull CallExecution execution) {
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
    private static @NotNull IdempotencyKey scopedKey(@NotNull CallExecution execution, @NotNull IdempotencyKey key) {
        return new IdempotencyKey(
                "call."
                        + DigestUtil.sha256Hex(
                                execution.methodSignature())
                        + ":"
                        + DigestUtil.sha256Hex(key.value()));
    }

    /**
     * 使用方法签名和字段顺序稳定的参数 JSON 生成请求指纹。
     *
     * @param execution 当前方法执行
     * @return 当前方法实参的稳定摘要
     */
    private @NotNull String fingerprint(@NotNull CallExecution execution) {
        return DigestUtil.sha256Hex(
                execution.methodSignature()
                        + ":"
                        + fingerprints.canonicalArguments(execution.arguments()));
    }

    /** 在表达式或解析器边界把允许的幂等键输入转换为领域类型。 */
    private static @NotNull IdempotencyKey normalizeKey(@NotNull Object value) {
        if (value instanceof IdempotencyKey key) {
            return IdempotencyKey.fromValue(key);
        }
        if (value instanceof CharSequence text) {
            return IdempotencyKey.fromValue(text);
        }
        throw new IllegalStateException("幂等键必须是 IdempotencyKey 或非空文本");
    }

}
