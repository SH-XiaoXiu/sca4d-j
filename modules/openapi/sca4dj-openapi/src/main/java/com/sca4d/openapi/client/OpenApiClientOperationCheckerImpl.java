package com.sca4d.openapi.client;

import com.sca4d.core.api.call.Call;
import com.sca4d.core.api.error.BusinessException;
import com.sca4d.core.api.error.CoreErrorCode;
import com.sca4d.core.api.identity.Account;
import com.sca4d.core.api.name.code.TreeCode;
import com.sca4d.openapi.web.OpenApiClientOperationChecker;
import org.jetbrains.annotations.NotNull;

import java.util.Objects;

/** 使用 OpenAPI Client 表和 Operation 关系检查当前调用。 */
public class OpenApiClientOperationCheckerImpl implements OpenApiClientOperationChecker {

    private final @NotNull OpenApiClientService clients;

    /** 创建 Client Operation 检查器。 */
    public OpenApiClientOperationCheckerImpl(@NotNull OpenApiClientService clients) {
        this.clients = Objects.requireNonNull(clients, "OpenAPI Client 服务不能为空");
    }

    /** 拒绝停用、未知或未开放目标 Operation 的 Client。 */
    @Override
    public void verifyAllowed(@NotNull Call call, @NotNull TreeCode operation) {
        long accountId = Objects.requireNonNull(call, "调用不能为空")
                .account()
                .map(Account::accountId)
                .orElseThrow(() -> new BusinessException(CoreErrorCode.AUTHENTICATION_REQUIRED));
        OpenApiClient client;
        try {
            client = clients.findByAccountId(accountId);
        } catch (IllegalArgumentException exception) {
            throw new BusinessException(CoreErrorCode.PERMISSION_DENIED);
        }
        if (!client.active() || !clients.allows(client.clientId(), operation)) {
            throw new BusinessException(CoreErrorCode.PERMISSION_DENIED);
        }
    }
}
