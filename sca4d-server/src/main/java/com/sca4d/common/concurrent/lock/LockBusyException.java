package com.sca4d.common.concurrent.lock;

import com.sca4d.common.exception.BizErrorCode;
import com.sca4d.common.exception.BizException;
import com.sca4d.common.exception.CommonErrorCode;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * 锁繁忙异常。
 */
public class LockBusyException extends BizException {

    /**
     * 创建默认锁繁忙异常。
     */
    public LockBusyException() {
        super(CommonErrorCode.LOCK_BUSY);
    }

    /**
     * 创建锁繁忙异常。
     *
     * @param errorCode 已注册锁错误码
     * @param cause 原始异常
     */
    public LockBusyException(@NotNull BizErrorCode errorCode, @Nullable Throwable cause) {
        super(errorCode, cause);
    }
}
