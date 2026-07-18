package com.sca4d.common.concurrent;

import static com.sca4d.common.exception.ResponseCode.CONFLICT;
import static com.sca4d.common.exception.ResponseCode.PRECONDITION_FAILED;

import com.sca4d.common.exception.BizErrorCode;
import com.sca4d.common.exception.BizException;
import com.sca4d.common.exception.CommonErrorCode;
import com.sca4d.common.web.ClientStateTarget;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * 并发修改冲突异常。
 *
 * <p>业务写入受乐观锁或版本号保护时，如果提交版本已经过期，应抛出该异常。Web 层按错误码返回 409，
 * 使用 If-Match 的资源返回 412，并按 refreshTarget 写入前端状态刷新响应头。
 */
public class ConcurrencyConflictException extends BizException {

    private final ClientStateTarget refreshTarget;

    /**
     * 创建默认并发冲突异常。
     */
    public ConcurrencyConflictException() {
        this(CommonErrorCode.CONCURRENCY_CONFLICT, null);
    }

    /**
     * 创建指定刷新目标的并发冲突异常。
     *
     * @param refreshTarget 前端需要重新拉取的状态目标
     */
    public ConcurrencyConflictException(@Nullable ClientStateTarget refreshTarget) {
        this(CommonErrorCode.CONCURRENCY_CONFLICT, refreshTarget);
    }

    /**
     * 创建使用指定业务错误码的并发冲突异常。
     *
     * @param errorCode 分类必须为 CONFLICT 或 PRECONDITION_FAILED 的业务模块错误码
     * @param refreshTarget 前端需要重新拉取的状态目标
     */
    public ConcurrencyConflictException(
            @NotNull BizErrorCode errorCode, @Nullable ClientStateTarget refreshTarget) {
        super(errorCode);
        if (errorCode.getCategory() != CONFLICT && errorCode.getCategory() != PRECONDITION_FAILED) {
            throw new IllegalArgumentException(
                    "并发冲突错误码必须属于 CONFLICT 或 PRECONDITION_FAILED: " + errorCode.getCode());
        }
        this.refreshTarget = refreshTarget;
    }

    /**
     * 返回前端需要重新拉取的数据名称。
     *
     * @return 未指定目标时由 Web 层标记全局 stale，否则刷新指定状态域
     */
    public @Nullable ClientStateTarget getRefreshTarget() {
        return refreshTarget;
    }
}
