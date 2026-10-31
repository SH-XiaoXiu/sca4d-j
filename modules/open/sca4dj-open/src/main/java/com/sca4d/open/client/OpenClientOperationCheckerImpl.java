package com.sca4d.open.client;

import com.sca4d.core.api.call.Call;
import com.sca4d.core.api.error.CoreErrorCode;
import com.sca4d.core.api.error.exception.BusinessException;
import com.sca4d.core.api.identity.Account;
import com.sca4d.core.api.identity.SubjectType;
import com.sca4d.core.api.name.code.TreeCode;
import org.jetbrains.annotations.NotNull;

import java.util.Objects;

/** 使用 Open Client 表和 Operation 关系检查当前调用。 */
public class OpenClientOperationCheckerImpl implements OpenClientOperationChecker {

    private final @NotNull OpenClientService clients;

    /** 使用持久化 Client 服务检查 Open 调用；服务为空时构造失败。 */
    public OpenClientOperationCheckerImpl(@NotNull OpenClientService clients) {
        this.clients = Objects.requireNonNull(clients, "Open Client 服务不能为空");
    }

    /** 拒绝停用、未知或未开放目标 Operation 的 Client。 */
    @Override
    public void verifyAllowed(@NotNull Call call, @NotNull TreeCode operation) {
        Call checkedCall = Objects.requireNonNull(call, "调用不能为空");
        if (!checkedCall.subject()
                .map(subject -> subject.type() == SubjectType.APPLICATION)
                .orElse(false)) {
            throw new BusinessException(CoreErrorCode.AUTHENTICATION_REQUIRED);
        }
        long accountId = checkedCall
                .account()
                .map(Account::accountId)
                .orElseThrow(() -> new BusinessException(CoreErrorCode.AUTHENTICATION_REQUIRED));
        OpenClient client;
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
