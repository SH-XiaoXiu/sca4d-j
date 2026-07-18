package com.sca4d.identity.external.view;

import com.sca4d.identity.IdentityProviderProtocol;
import com.sca4d.identity.IdentityProviderStatus;
import com.sca4d.identity.external.ExternalIdentity;
import com.sca4d.identity.external.ExternalIdentityDetails;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.jetbrains.annotations.NotNull;

import java.time.OffsetDateTime;

/** 当前账号可以展示的 ExternalIdentity 和 Provider 摘要。 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ExternalIdentityView {

    private Long identityId;
    private Long providerId;
    private String providerCode;
    private String providerName;
    private IdentityProviderProtocol protocol;
    private IdentityProviderStatus providerStatus;
    private String externalSubject;
    private String displayName;
    private String email;
    private Boolean emailVerified;
    private String avatarUri;
    private OffsetDateTime lastAuthenticatedAt;
    private Long version;

    public static @NotNull ExternalIdentityView from(
            @NotNull ExternalIdentityDetails details) {
        ExternalIdentity identity = details.getIdentity();
        return new ExternalIdentityView(
                identity.getIdentityId(),
                identity.getProviderId(),
                details.getProviderCode(),
                details.getProviderName(),
                details.getProviderProtocol(),
                details.getProviderStatus(),
                identity.getProfile().getExternalSubject(),
                identity.getProfile().getDisplayName(),
                identity.getProfile().getEmail(),
                identity.getProfile().getEmailVerified(),
                identity.getProfile().getAvatarUri(),
                identity.getLastAuthenticatedAt(),
                identity.getVersion());
    }
}
