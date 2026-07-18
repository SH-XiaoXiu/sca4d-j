package com.sca4d.authentication.passkey;

import com.sca4d.authentication.AccountSecondFactorMethod;
import com.sca4d.entity.IamAccountEntity;
import com.sca4d.iam.passkey.repository.PasskeyRepository;
import lombok.RequiredArgsConstructor;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/** 检查 Passkey 方式所需的当前 ACTIVE Credential。 */
@Component
@ConditionalOnProperty(prefix = "sca4dj.authentication.passkey", name = "enabled", havingValue = "true")
@RequiredArgsConstructor
public class PasskeySecondFactorMethod implements AccountSecondFactorMethod {

    private final PasskeyRepository repository;

    @Override
    public boolean available(
            @NotNull IamAccountEntity account, @Nullable Long excludedCredentialId) {
        return repository.hasActiveCredential(account.getId(), excludedCredentialId);
    }
}
