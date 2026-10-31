package com.sca4d.app.server.platform;

import com.sca4d.auth.email.EmailCodeAuthenticationMethod;
import com.sca4d.core.api.error.exception.BusinessException;
import com.sca4d.core.api.identity.AccountIdentifierType;
import com.sca4d.iam.identity.AccountIdentifierNormalizer;
import com.sca4d.iam.identity.IamErrorCode;
import com.sca4d.notification.email.EmailAddresses;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import org.springframework.stereotype.Component;

import java.util.Locale;

/** 把 Sca4d Server Profile 邮箱转换为 `EMAIL` 登录标识使用的稳定小写地址。 */
@Component
public class PlatformEmailAccountIdentifierNormalizer implements AccountIdentifierNormalizer {

    /** 创建 Platform 邮箱登录标识规范化器。 */
    public PlatformEmailAccountIdentifierNormalizer() {
    }

    @Override
    @Contract(pure = true)
    public @NotNull AccountIdentifierType type() {
        return EmailCodeAuthenticationMethod.EMAIL_IDENTIFIER_TYPE;
    }

    @Override
    public @NotNull String normalize(@NotNull String rawValue) {
        try {
            return EmailAddresses.require(rawValue.trim()).toLowerCase(Locale.ROOT);
        } catch (IllegalArgumentException exception) {
            throw new BusinessException(IamErrorCode.IDENTIFIER_INVALID, exception);
        }
    }
}
