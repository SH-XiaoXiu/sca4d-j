package com.sca4d.entity;

import com.mybatisflex.annotation.Id;
import com.mybatisflex.annotation.Table;
import lombok.Data;
import lombok.experimental.Accessors;

import java.time.OffsetDateTime;

/** {@code iam_external_identity} 保存的 Provider external subject 与 Account 绑定。 */
@Data
@Accessors(chain = true)
@Table("iam_external_identity")
public class IamExternalIdentityEntity {

    @Id
    private Long id;
    private Long providerId;
    private Long accountId;
    private String externalSubject;
    private String displayName;
    private String email;
    private Boolean emailVerified;
    private String avatarUri;
    private OffsetDateTime lastAuthenticatedAt;
    private OffsetDateTime createdAt;
    private OffsetDateTime updatedAt;
    private Long version;
    private Integer deleted;
}
