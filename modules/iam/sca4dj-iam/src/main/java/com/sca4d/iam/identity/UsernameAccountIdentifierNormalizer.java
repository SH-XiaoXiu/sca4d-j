package com.sca4d.iam.identity;

import com.sca4d.core.api.error.BusinessException;
import com.sca4d.core.api.identity.AccountIdentifierType;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;

import java.text.Normalizer;
import java.util.Locale;

/** IAM 内置的用户名规范化规则。 */
public final class UsernameAccountIdentifierNormalizer implements AccountIdentifierNormalizer {
    /** 创建内置用户名规范化器。 */
    public UsernameAccountIdentifierNormalizer() {
    }

    /** {@inheritDoc} */
    @Override
    @Contract(pure = true)
    public @NotNull AccountIdentifierType type() {
        return AccountIdentifierType.USERNAME;
    }

    /** {@inheritDoc} */
    @Override
    public @NotNull String normalize(@NotNull String rawValue) {
        if (rawValue.isBlank()) {
            throw new BusinessException(IamErrorCode.IDENTIFIER_INVALID);
        }
        String normalized = Normalizer.normalize(rawValue.trim(), Normalizer.Form.NFKC)
                .toLowerCase(Locale.ROOT);
        if (normalized.isEmpty()
                || normalized.length() > 128
                || normalized.indexOf(':') >= 0
                || normalized.codePoints().anyMatch(Character::isISOControl)) {
            throw new BusinessException(IamErrorCode.IDENTIFIER_INVALID);
        }
        return normalized;
    }
}
