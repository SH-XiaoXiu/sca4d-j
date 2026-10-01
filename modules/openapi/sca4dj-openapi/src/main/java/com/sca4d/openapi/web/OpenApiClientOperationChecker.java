package com.sca4d.openapi.web;

import com.sca4d.core.api.call.Call;
import com.sca4d.core.api.name.code.TreeCode;
import org.jetbrains.annotations.NotNull;

/** 判断当前已认证 Application Client 是否被允许调用声明的 OpenAPI Operation。 */
public interface OpenApiClientOperationChecker {

    /**
     * 检查一次 OpenAPI 调用。
     *
     * @param call 当前 HTTP 调用，必须已经完成认证
     * @param operation 路由声明的 Operation 编码
     */
    void verifyAllowed(@NotNull Call call, @NotNull TreeCode operation);
}
