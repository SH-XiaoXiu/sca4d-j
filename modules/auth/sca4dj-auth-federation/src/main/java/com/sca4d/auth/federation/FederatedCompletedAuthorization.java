package com.sca4d.auth.federation;

import java.util.Objects;

/** 回调完成后交给认证方法的一次性外部资料。 */
@SuppressWarnings("ClassCanBeRecord") // 项目统一使用普通 class，不使用 Java record。
public class FederatedCompletedAuthorization {
    private final FederatedAuthorizationPurpose purpose;

    private final FederatedProfile profile;

    private final String providerCode;

    public FederatedCompletedAuthorization(
            String providerCode,
            FederatedAuthorizationPurpose purpose,
            FederatedProfile profile) {
        this.providerCode = Objects.requireNonNull(providerCode, "Provider 编码不能为空");
        this.purpose = Objects.requireNonNull(purpose, "授权用途不能为空");
        this.profile = Objects.requireNonNull(profile, "外部资料不能为空");
    }

    /** @return Provider 编码 */
    public String providerCode() {
        return providerCode;
    }

    /** @return 当前完成结果允许执行的业务动作 */
    public FederatedAuthorizationPurpose purpose() {
        return purpose;
    }

    /** @return 已验证外部资料 */
    public FederatedProfile profile() {
        return profile;
    }
}
