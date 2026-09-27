package com.sca4d.auth.federation;

import org.jetbrains.annotations.Nullable;

/** 外部 Provider 返回的标准化用户资料，不包含 Access Token。 */
public class FederatedProfile {

    private final String issuer;

    private final String subject;

    private final String displayName;

    private final String email;

    private final boolean emailVerified;

    private final String avatarUrl;

    /** 创建标准化外部用户资料。 */
    public FederatedProfile(
            @Nullable String issuer,
            String subject,
            @Nullable String displayName,
            @Nullable String email,
            boolean emailVerified,
            @Nullable String avatarUrl) {
        if (subject == null || subject.isBlank())
            throw new IllegalArgumentException("外部 subject 不能为空");
        this.issuer = issuer == null || issuer.isBlank() ? null : issuer.strip();
        this.subject = subject.strip();
        this.displayName = clean(displayName);
        this.email = clean(email);
        this.emailVerified = emailVerified;
        this.avatarUrl = clean(avatarUrl);
    }

    /** @return OIDC issuer；OAuth2 Provider 返回 null */
    public String issuer() {
        return issuer;
    }

    /** @return Provider 内稳定的用户标识 */
    public String subject() {
        return subject;
    }

    /** @return 展示名称 */
    public String displayName() {
        return displayName;
    }

    /** @return 外部邮箱 */
    public String email() {
        return email;
    }

    /** @return Provider 明确声明邮箱已验证 */
    public boolean emailVerified() {
        return emailVerified;
    }

    /** @return 外部头像地址，仅作为快照保存 */
    public String avatarUrl() {
        return avatarUrl;
    }

    private static String clean(String value) {
        return value == null || value.isBlank() ? null : value.strip();
    }
}
