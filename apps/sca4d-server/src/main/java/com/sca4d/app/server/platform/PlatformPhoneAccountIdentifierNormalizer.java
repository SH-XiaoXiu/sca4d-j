package com.sca4d.app.server.platform;

import com.sca4d.auth.sms.SmsCodeAuthenticationMethod;
import com.sca4d.core.api.error.exception.BusinessException;
import com.sca4d.core.api.identity.AccountIdentifierType;
import com.sca4d.core.api.region.RegionCatalog;
import com.sca4d.iam.identity.AccountIdentifierNormalizer;
import com.sca4d.iam.identity.IamErrorCode;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import org.springframework.stereotype.Component;

/** 把 Platform 手机登录标识规范为已经校验的 E.164 文本。 */
@Component
public class PlatformPhoneAccountIdentifierNormalizer implements AccountIdentifierNormalizer {

    private final RegionCatalog regions = RegionCatalog.standard();

    @Override
    @Contract(pure = true)
    public @NotNull AccountIdentifierType type() {
        return SmsCodeAuthenticationMethod.PHONE_IDENTIFIER_TYPE;
    }

    @Override
    public @NotNull String normalize(@NotNull String rawValue) {
        var parsed = regions.parse(rawValue.strip());
        if (parsed == null) {
            throw new BusinessException(IamErrorCode.IDENTIFIER_INVALID);
        }
        return parsed.value();
    }
}
