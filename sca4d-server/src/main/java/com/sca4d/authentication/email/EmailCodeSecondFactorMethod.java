package com.sca4d.authentication.email;

import com.sca4d.authentication.AccountSecondFactorMethod;
import com.sca4d.entity.IamAccountEntity;
import com.sca4d.entity.IamContactEntity;
import com.sca4d.iam.contact.ContactService;
import com.sca4d.iam.contact.ContactType;
import lombok.RequiredArgsConstructor;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/** 检查邮件验证码方式所需的当前已验证 EMAIL Contact。 */
@Component
@ConditionalOnProperty(prefix = "sca4dj.authentication.email-code", name = "enabled", havingValue = "true")
@RequiredArgsConstructor
public class EmailCodeSecondFactorMethod implements AccountSecondFactorMethod {

    private final ContactService contacts;

    @Override
    public boolean available(
            @NotNull IamAccountEntity account, @Nullable Long excludedCredentialId) {
        IamContactEntity email = contacts.current(account.getSubjectId(), ContactType.EMAIL);
        return email != null && email.getVerifiedAt() != null;
    }
}
