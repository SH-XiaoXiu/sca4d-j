package com.sca4d.entity;

import com.mybatisflex.annotation.Id;
import com.mybatisflex.annotation.Table;
import lombok.Data;
import lombok.experimental.Accessors;

import java.time.OffsetDateTime;

/** OIDC Discovery、Client、scope 和加密 Client Secret 的持久化实体。 */
@Data
@Accessors(chain = true)
@Table("iam_oidc_provider")
public class IamOidcProviderEntity {

    @Id
    private Long providerId;
    private String issuer;
    private String clientId;
    private String clientSecretKeyId;
    private byte[] clientSecretNonce;
    private byte[] clientSecretCiphertext;
    private String scopes;
    private OffsetDateTime createdAt;
    private OffsetDateTime updatedAt;
}
