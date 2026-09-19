package com.sca4d.core.api.concurrent;

import com.sca4d.core.api.error.BusinessException;
import com.sca4d.core.api.error.CoreErrorCode;

/** 表示调用方提交的预期版本与持久化对象的当前版本不一致。 */
public final class VersionPreconditionFailedException extends BusinessException {

    /**
     * 创建版本前置条件失败异常。
     *
     * @param target 发生版本冲突的对象说明
     */
    public VersionPreconditionFailedException(String target) {
        super(CoreErrorCode.CONFLICT, null, target);
    }
}
