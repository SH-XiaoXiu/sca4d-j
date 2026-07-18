package com.sca4d.identity;

/** IdentityProvider 管理审计和资源引用使用的对象类型。 */
public final class IdentityProviderResourceTypes {

    /** IdentityProvider 配置。 */
    public static final String IDENTITY_PROVIDER = "identity-provider";
    /** Account 与外部身份源主体的关联。 */
    public static final String EXTERNAL_IDENTITY = "external-identity";

    /** 禁止创建资源类型常量容器。 */
    private IdentityProviderResourceTypes() {
    }
}
