package com.sca4d.entity;

import com.mybatisflex.annotation.Table;
import com.sca4d.common.auth.AccountScopeType;
import com.sca4d.identity.IdentityProviderProtocol;
import com.sca4d.identity.IdentityProviderStatus;
import com.sca4d.identity.policy.IdentityProviderRegistrationPolicy;
import lombok.Data;
import lombok.experimental.Accessors;

import java.time.OffsetDateTime;

/** IdentityProvider 通用作用域、注册策略、启停状态和版本的持久化实体。 */
@Data
@Accessors(chain = true)
@Table("iam_identity_provider")
public class IamIdentityProviderEntity {

    private Long id;
    private AccountScopeType scopeType;
    private Long scopeId;
    private String code;
    private String name;
    private IdentityProviderProtocol protocol;
    private IdentityProviderRegistrationPolicy registrationPolicy;
    private Boolean emailAutoLink;
    private IdentityProviderStatus status;
    private OffsetDateTime createdAt;
    private OffsetDateTime updatedAt;
    private Long version;
    private Integer deleted;
}
