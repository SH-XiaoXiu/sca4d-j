package com.sca4d.authentication.totp;

import com.sca4d.authentication.AccountSecondFactorMethod;
import com.sca4d.entity.IamAccountEntity;
import com.sca4d.iam.totp.repository.TotpRepository;
import lombok.RequiredArgsConstructor;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.springframework.stereotype.Component;

/** 检查 TOTP 方式所需的当前 ACTIVE Credential。 */
@Component
@RequiredArgsConstructor
public class TotpSecondFactorMethod implements AccountSecondFactorMethod {

    private final TotpRepository repository;

    @Override
    public boolean available(
            @NotNull IamAccountEntity account, @Nullable Long excludedCredentialId) {
        return repository.hasActiveCredential(account.getId(), excludedCredentialId);
    }
}
