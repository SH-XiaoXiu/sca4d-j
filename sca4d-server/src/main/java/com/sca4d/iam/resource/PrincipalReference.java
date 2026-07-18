package com.sca4d.iam.resource;

import com.sca4d.common.auth.PrincipalType;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/** 资源引用解析所需的 Principal 安全事实。 */
public class PrincipalReference {

    private final Long principalId;
    private final Long subjectId;
    private final Long tenantId;
    private final PrincipalType principalType;
    private final String displayName;
    private final boolean deleted;

    public PrincipalReference(
            @NotNull Long principalId,
            @Nullable Long subjectId,
            @Nullable Long tenantId,
            @Nullable PrincipalType principalType,
            @Nullable String displayName,
            boolean deleted) {
        this.principalId = principalId;
        this.subjectId = subjectId;
        this.tenantId = tenantId;
        this.principalType = principalType;
        this.displayName = displayName;
        this.deleted = deleted;
    }

    public @NotNull Long getPrincipalId() {
        return principalId;
    }

    public @Nullable Long getSubjectId() {
        return subjectId;
    }

    public @Nullable Long getTenantId() {
        return tenantId;
    }

    public @Nullable PrincipalType getPrincipalType() {
        return principalType;
    }

    public @Nullable String getDisplayName() {
        return displayName;
    }

    public boolean isDeleted() {
        return deleted;
    }
}
