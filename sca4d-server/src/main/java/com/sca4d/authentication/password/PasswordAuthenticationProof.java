package com.sca4d.authentication.password;

import com.sca4d.common.auth.PasswordProof;
import com.sca4d.iam.identifier.AccountIdentifierType;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/** PasswordAuthMethod 验证一次作用域内账号所需的标识和密码 Proof。 */
public final class PasswordAuthenticationProof {

    private final @Nullable AccountIdentifierType identifierType;
    private final @Nullable String identifier;
    private final PasswordProof password;

    public PasswordAuthenticationProof(
            @Nullable AccountIdentifierType identifierType,
            @Nullable String identifier,
            @NotNull PasswordProof password) {
        this.identifierType = identifierType;
        this.identifier = identifier;
        this.password = password;
    }

    public @Nullable AccountIdentifierType identifierType() {
        return identifierType;
    }

    public @Nullable String identifier() {
        return identifier;
    }

    public @NotNull PasswordProof password() {
        return password;
    }
}
