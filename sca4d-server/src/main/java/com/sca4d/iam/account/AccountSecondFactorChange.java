package com.sca4d.iam.account;

import com.sca4d.entity.IamAccountEntity;
import com.sca4d.iam.recovery.RecoveryCodeSummary;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/** Account 第二因子要求修改结果及本次一次性展示的恢复码。 */
public class AccountSecondFactorChange {

    private final IamAccountEntity account;
    private final RecoveryCodeSummary recoveryCodeSummary;
    private final List<String> recoveryCodes;

    /**
     * 创建第二因子要求修改结果。
     *
     * @param account 修改后的 Account
     * @param recoveryCodeSummary 启用或重新签发后的集合状态；关闭时为空
     * @param recoveryCodes 仅在本次启用或重新签发响应中出现的明文恢复码
     */
    public AccountSecondFactorChange(
            @NotNull IamAccountEntity account,
            @Nullable RecoveryCodeSummary recoveryCodeSummary,
            @NotNull List<String> recoveryCodes) {
        this.account = account;
        this.recoveryCodeSummary = recoveryCodeSummary;
        this.recoveryCodes = List.copyOf(recoveryCodes);
    }

    /** @return 修改后的 Account */
    public @NotNull IamAccountEntity getAccount() {
        return account;
    }

    /** @return 当前恢复码集合状态；第二因子关闭时为空 */
    public @Nullable RecoveryCodeSummary getRecoveryCodeSummary() {
        return recoveryCodeSummary;
    }

    /** @return 仅允许本次响应展示的恢复码；关闭时为空集合 */
    public @NotNull List<String> getRecoveryCodes() {
        return recoveryCodes;
    }
}
