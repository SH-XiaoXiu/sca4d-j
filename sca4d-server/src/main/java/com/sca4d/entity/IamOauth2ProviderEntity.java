package com.sca4d.entity;

import com.mybatisflex.annotation.Id;
import com.mybatisflex.annotation.Table;
import lombok.Data;
import lombok.experimental.Accessors;

import java.time.OffsetDateTime;

/** OAuth2 端点、Client、scope、Profile 映射和加密 Client Secret 的持久化实体。 */
@Data
@Accessors(chain = true)
@Table("iam_oauth2_provider")
public class IamOauth2ProviderEntity {

    @Id
    private Long providerId;
    private String authorizationUri;
    private String tokenUri;
    private String profileUri;
    private String auxiliaryProfileUri;
    private String clientId;
    private String clientSecretKeyId;
    private byte[] clientSecretNonce;
    private byte[] clientSecretCiphertext;
    private String scopes;
    private String subjectPath;
    private String displayNamePath;
    private String emailPath;
    private String emailVerifiedPath;
    private String avatarPath;
    private OffsetDateTime createdAt;
    private OffsetDateTime updatedAt;
}
