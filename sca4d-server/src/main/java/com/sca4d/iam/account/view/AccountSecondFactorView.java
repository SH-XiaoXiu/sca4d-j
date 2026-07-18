package com.sca4d.iam.account.view;

import com.sca4d.iam.account.AccountSecondFactorChange;
import com.sca4d.iam.recovery.RecoveryCodeSummary;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.jetbrains.annotations.NotNull;

import java.time.OffsetDateTime;
import java.util.List;

/** Account 第二因子要求及仅本次展示的恢复码响应。 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class AccountSecondFactorView {

    private boolean required;
    private Long accountVersion;
    private OffsetDateTime recoveryCodesIssuedAt;
    private int recoveryCodesRemaining;
    private List<String> recoveryCodes;

    /** @return 修改后的设置；恢复码明文只在签发响应中出现 */
    public static @NotNull AccountSecondFactorView from(
            @NotNull AccountSecondFactorChange change) {
        RecoveryCodeSummary summary = change.getRecoveryCodeSummary();
        return new AccountSecondFactorView(
                Boolean.TRUE.equals(change.getAccount().getSecondFactorRequired()),
                change.getAccount().getVersion(),
                summary == null ? null : summary.getIssuedAt(),
                summary == null ? 0 : summary.getRemainingCount(),
                change.getRecoveryCodes());
    }
}
