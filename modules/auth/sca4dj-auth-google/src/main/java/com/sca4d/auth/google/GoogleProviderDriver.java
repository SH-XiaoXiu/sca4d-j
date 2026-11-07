package com.sca4d.auth.google;

import com.sca4d.auth.federation.FederatedClaims;
import com.sca4d.auth.federation.FederatedErrorCode;
import com.sca4d.auth.federation.FederatedProfile;
import com.sca4d.auth.federation.FederatedProviderConfiguration;
import com.sca4d.auth.federation.FederatedProviderDriver;
import com.sca4d.core.api.error.exception.BusinessException;
import com.sca4d.core.api.name.code.TreeCode;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 把 Google ID Token 的 claims 转换为联邦资料。
 *
 * <p>Provider 的 {@code authorizationParameters.hd} 非空时，ID Token 的 {@code hd} claim 必须与它一致；
 * 值为 {@code *} 时只要求 ID Token 带有 {@code hd}。授权页的 {@code hd} 参数只是界面提示，这里的比较才是限制。
 */
public final class GoogleProviderDriver implements FederatedProviderDriver {

    /** Provider 配置选择本 Driver 时使用的编码。 */
    public static final TreeCode CODE = TreeCode.of("GOOGLE");

    private static final Logger LOG = LoggerFactory.getLogger(GoogleProviderDriver.class);

    @Override
    public @NotNull TreeCode code() {
        return CODE;
    }

    /**
     * @throws BusinessException 缺少 {@code sub} 时抛出 PROFILE_INVALID；{@code hd} 与 Provider 配置不一致时抛出 PROFILE_DOMAIN_REJECTED
     */
    @Override
    public @NotNull FederatedProfile profile(
            @NotNull FederatedClaims claims,
            @NotNull FederatedProviderConfiguration configuration) {
        String subject = claims.subject();
        String requiredDomain = configuration.authorizationParameters().get("hd");
        if (requiredDomain != null) {
            // gmail.com 等个人账号没有 hd claim；配置了域名限制时它们同样被拒绝。
            String domain = claims.text("hd");
            if (domain == null || !"*".equals(requiredDomain) && !domain.equalsIgnoreCase(requiredDomain)) {
                LOG.debug("Google 资料不属于允许的域 | hostedDomainPresent={}", domain != null);
                throw new BusinessException(FederatedErrorCode.PROFILE_DOMAIN_REJECTED);
            }
        }
        return new FederatedProfile(
                configuration.issuer(),
                subject,
                claims.text("name"),
                claims.text("email"),
                claims.flag("email_verified"),
                claims.text("picture"));
    }
}
