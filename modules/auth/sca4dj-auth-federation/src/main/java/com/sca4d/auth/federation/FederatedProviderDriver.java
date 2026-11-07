package com.sca4d.auth.federation;

import com.sca4d.core.api.error.exception.BusinessException;
import com.sca4d.core.api.name.code.TreeCode;
import org.jetbrains.annotations.NotNull;

/**
 * 把一类外部 Provider 返回的 claims 转换为 {@link FederatedProfile}。
 *
 * <p>Driver 由可选认证模块以 Spring Bean 提供，按 {@link #code()} 登记到
 * {@link FederatedProviderDriverRegistry}。Provider 的协议配置通过
 * {@link FederatedProviderConfiguration#driver()} 选择 Driver；没有选择 Driver 的 Provider 使用标准
 * OIDC claims 或 {@code profileMapping}。
 */
public interface FederatedProviderDriver {

    /**
     * 返回登记和 Provider 配置使用的 Driver 编码，例如 {@code GOOGLE}。
     *
     * @return 不含通配节点、冒号文本不超过 64 个字符的编码
     */
    @NotNull
    TreeCode code();

    /**
     * 校验并转换 claims。
     *
     * @param claims 已完成协议校验的 claims
     * @param configuration 当前 Provider 的协议配置，提供 issuer 和附加授权参数
     * @return 不包含 Token 的外部资料
     * @throws BusinessException claims 不满足 Provider 配置的限制时抛出
     */
    @NotNull
    FederatedProfile profile(@NotNull FederatedClaims claims, @NotNull FederatedProviderConfiguration configuration);

    /**
     * 签发 Token 端点使用的 client_secret。仅 {@link FederatedClientAuthentication#CLIENT_SECRET_SIGNED_JWT} 的 Provider 调用，
     * 每次授权码交换调用一次。
     *
     * @param configuration 当前 Provider 的协议配置，其中 {@code clientSecret()} 是解密后的签名材料
     * @return 发送给 Token 端点的 client_secret 文本
     * @throws BusinessException Driver 不签发 client_secret，或签名材料无效时抛出 {@link FederatedErrorCode#CONFIGURATION_INVALID}
     */
    default @NotNull String clientSecret(@NotNull FederatedProviderConfiguration configuration) {
        throw new BusinessException(FederatedErrorCode.CONFIGURATION_INVALID);
    }
}
