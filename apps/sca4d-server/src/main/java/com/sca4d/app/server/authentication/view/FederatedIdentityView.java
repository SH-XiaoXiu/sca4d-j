package com.sca4d.app.server.authentication.view;

import com.sca4d.auth.federation.FederatedIdentity;
import org.jetbrains.annotations.NotNull;

/** 返回当前账号已绑定的外部身份和最近资料快照。 */
public class FederatedIdentityView {
    private final long id;

    private final long providerId;

    private final long accountId;

    private final String issuer;

    private final String subject;

    private final String displayName;

    private final String email;

    private final boolean emailVerified;

    private final String avatarUrl;

    private final long version;

    private FederatedIdentityView(FederatedIdentity identity) {
        this.id = identity.id();
        this.providerId = identity.providerId();
        this.accountId = identity.accountId();
        this.issuer = identity.profile().issuer();
        this.subject = identity.profile().subject();
        this.displayName = identity.profile().displayName();
        this.email = identity.profile().email();
        this.emailVerified = identity.profile().emailVerified();
        this.avatarUrl = identity.profile().avatarUrl();
        this.version = identity.version();
    }

    /** @return 外部身份安全投影 */
    public static @NotNull FederatedIdentityView from(@NotNull FederatedIdentity identity) {
        return new FederatedIdentityView(identity);
    }

    public long getId() {
        return id;
    }

    public long getProviderId() {
        return providerId;
    }

    public long getAccountId() {
        return accountId;
    }

    public String getIssuer() {
        return issuer;
    }

    public String getSubject() {
        return subject;
    }

    public String getDisplayName() {
        return displayName;
    }

    public String getEmail() {
        return email;
    }

    public boolean isEmailVerified() {
        return emailVerified;
    }

    public String getAvatarUrl() {
        return avatarUrl;
    }

    public long getVersion() {
        return version;
    }
}
