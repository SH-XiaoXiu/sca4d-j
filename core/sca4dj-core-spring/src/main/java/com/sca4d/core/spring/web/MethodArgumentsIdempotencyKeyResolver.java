package com.sca4d.core.spring.web;

import com.sca4d.core.api.call.CallExecution;
import com.sca4d.core.api.call.CallValueResolver;
import com.sca4d.core.api.concurrent.IdempotencyFingerprints;
import com.sca4d.core.api.concurrent.IdempotencyKey;
import org.jetbrains.annotations.NotNull;

import java.util.Objects;

/** 使用 IdempotencyFingerprints 计算方法参数摘要，生成幂等键，不保存原始参数值。 */
public class MethodArgumentsIdempotencyKeyResolver implements CallValueResolver<IdempotencyKey> {

    private final IdempotencyFingerprints fingerprints;

    /**
     * 创建方法参数幂等键解析器。
     *
     * @param fingerprints 计算方法参数摘要的 IdempotencyFingerprints
     */
    public MethodArgumentsIdempotencyKeyResolver(IdempotencyFingerprints fingerprints) {
        this.fingerprints = Objects.requireNonNull(fingerprints, "幂等参数规范化入口不能为空");
    }

    /** {@inheritDoc} */
    @Override
    public @NotNull IdempotencyKey resolve(CallExecution execution) {
        return new IdempotencyKey(fingerprints.argumentsDigest(execution.arguments()));
    }
}
