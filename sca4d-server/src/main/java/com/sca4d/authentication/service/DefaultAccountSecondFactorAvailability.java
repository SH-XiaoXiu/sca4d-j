package com.sca4d.authentication.service;

import com.sca4d.authentication.AccountSecondFactorMethod;
import com.sca4d.entity.IamAccountEntity;
import com.sca4d.iam.account.AccountSecondFactorAvailability;
import org.jetbrains.annotations.NotNull;
import org.springframework.stereotype.Service;

import java.util.List;

/** 汇总当前应用已注册认证方式对指定 Account 的第二因素可用性判断。 */
@Service
public class DefaultAccountSecondFactorAvailability implements AccountSecondFactorAvailability {

    private final List<AccountSecondFactorMethod> methods;

    /**
     * 固定当前 Spring 容器注册的第二因素规则。
     *
     * @param methods 已启用认证方式提供的 Account 检查规则
     */
    public DefaultAccountSecondFactorAvailability(@NotNull List<AccountSecondFactorMethod> methods) {
        this.methods = List.copyOf(methods);
    }

    @Override
    public boolean available(@NotNull IamAccountEntity account) {
        // 每种方式只读取自己的认证资料；Account Service 不识别 EMAIL、TOTP 或后续新增协议。
        return methods.stream().anyMatch(method -> method.available(account, null));
    }

    @Override
    public boolean availableAfterRemovingCredential(
            @NotNull IamAccountEntity account, @NotNull Long credentialId) {
        // 即将删除的 Credential 不得继续证明删除后的 Account 仍能完成第二因素。
        return methods.stream().anyMatch(method -> method.available(account, credentialId));
    }
}
