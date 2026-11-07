package com.sca4d.auth.apple;

import com.sca4d.auth.federation.FederatedClaims;
import com.sca4d.auth.federation.FederatedProfile;
import com.sca4d.auth.federation.FederatedProviderConfiguration;
import com.sca4d.auth.federation.FederatedProviderDriver;
import com.sca4d.core.api.error.exception.BusinessException;
import com.sca4d.core.api.name.code.TreeCode;
import org.jetbrains.annotations.NotNull;

import java.util.Objects;

/**
 * 把 Apple ID Token 的 claims 转换为联邦资料，并为 Token 端点签发 {@code client_secret}。
 *
 * <p>Apple 的 {@code email_verified} 和 {@code is_private_email} 可能是文本 {@code "true"}。
 * 隐藏邮箱（{@code is_private_email}）是 Apple 的转发地址，资料仍保留该邮箱，但 {@code emailVerified} 为 false，
 * 因此不会按已验证邮箱自动关联已有 Account。ID Token 不含用户姓名，{@code displayName} 始终为空。
 */
public final class AppleProviderDriver implements FederatedProviderDriver {

    /** Provider 配置选择本 Driver 时使用的编码。 */
    public static final TreeCode CODE = TreeCode.of("APPLE");

    private final @NotNull AppleClientSecretGenerator secrets;

    /** 创建 Driver，{@code client_secret} 由指定生成器签发。 */
    public AppleProviderDriver(@NotNull AppleClientSecretGenerator secrets) {
        this.secrets = Objects.requireNonNull(secrets, "Apple client_secret 生成器不能为空");
    }

    @Override
    public @NotNull TreeCode code() {
        return CODE;
    }

    /**
     * @throws BusinessException 缺少 {@code sub} 时抛出 PROFILE_INVALID
     */
    @Override
    public @NotNull FederatedProfile profile(
            @NotNull FederatedClaims claims,
            @NotNull FederatedProviderConfiguration configuration) {
        boolean verified = claims.flag("email_verified") && !claims.flag("is_private_email");
        return new FederatedProfile(
                configuration.issuer(),
                claims.subject(),
                null,
                claims.text("email"),
                verified,
                null);
    }

    @Override
    public @NotNull String clientSecret(@NotNull FederatedProviderConfiguration configuration) {
        return secrets.generate(configuration);
    }
}
