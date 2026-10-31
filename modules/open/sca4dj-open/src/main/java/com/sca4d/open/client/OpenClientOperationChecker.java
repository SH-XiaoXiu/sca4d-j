package com.sca4d.open.client;

import com.sca4d.core.api.call.Call;
import com.sca4d.core.api.name.code.TreeCode;
import org.jetbrains.annotations.NotNull;

/** 检查当前开放 Client 是否允许调用指定 Operation。 */
public interface OpenClientOperationChecker {

    /**
     * 检查当前调用是否可以执行 Operation。
     *
     * @param call 当前已经完成认证的调用
     * @param operation 业务 Operation 编码
     */
    void verifyAllowed(@NotNull Call call, @NotNull TreeCode operation);
}
