package com.sca4d.auth.federation;

import java.time.Instant;
import java.util.Objects;

/** Redis 中保存的单次外部授权请求状态。 */
@SuppressWarnings("ClassCanBeRecord") // 项目统一使用普通 class，不使用 Java record。
public final class FederatedAuthorization {

    private final String id;

    private final String providerCode;

    private final FederatedAuthorizationPurpose purpose;

    private final String browserBinding;

    private final String nonce;

    private final String codeVerifier;

    private final String redirectUri;

    private final Instant expiresAt;

    /** 创建授权状态。 */
    public FederatedAuthorization(
            String id,
            String providerCode,
            FederatedAuthorizationPurpose purpose,
            String browserBinding,
            String nonce,
            String codeVerifier,
            String redirectUri,
            Instant expiresAt) {
        this.id = Objects.requireNonNull(id, "授权状态标识不能为空");
        this.providerCode = Objects.requireNonNull(providerCode, "Provider 编码不能为空");
        this.purpose = Objects.requireNonNull(purpose, "授权用途不能为空");
        this.browserBinding = Objects.requireNonNull(browserBinding, "浏览器绑定不能为空");
        this.nonce = Objects.requireNonNull(nonce, "Nonce 不能为空");
        this.codeVerifier = Objects.requireNonNull(codeVerifier, "PKCE verifier 不能为空");
        this.redirectUri = Objects.requireNonNull(redirectUri, "回调 URI 不能为空");
        this.expiresAt = Objects.requireNonNull(expiresAt, "授权过期时间不能为空");
    }

    /** @return 一次性状态 ID */
    public String id() {
        return id;
    }

    /** @return Provider 编码 */
    public String providerCode() {
        return providerCode;
    }

    /** @return 当前授权结果允许执行的业务动作 */
    public FederatedAuthorizationPurpose purpose() {
        return purpose;
    }

    /** @return 浏览器摘要或 Session 摘要 */
    public String browserBinding() {
        return browserBinding;
    }

    /** @return OIDC Nonce */
    public String nonce() {
        return nonce;
    }

    /** @return PKCE verifier */
    public String codeVerifier() {
        return codeVerifier;
    }

    /** @return 开始授权时使用的固定回调 URI */
    public String redirectUri() {
        return redirectUri;
    }

    /** @return 到期时间 */
    public Instant expiresAt() {
        return expiresAt;
    }
}
