package com.sca4d.iam.resource;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/** 资源引用解析所需的 Subject 安全事实。 */
public class SubjectReference {

    private final Long subjectId;
    private final String displayName;
    private final boolean deleted;

    public SubjectReference(@NotNull Long subjectId, @Nullable String displayName, boolean deleted) {
        this.subjectId = subjectId;
        this.displayName = displayName;
        this.deleted = deleted;
    }

    public @NotNull Long getSubjectId() {
        return subjectId;
    }

    public @Nullable String getDisplayName() {
        return displayName;
    }

    public boolean isDeleted() {
        return deleted;
    }
}
