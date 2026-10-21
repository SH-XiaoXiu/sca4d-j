package com.sca4d.audit.recording;

import com.sca4d.audit.recording.mapper.EncodedAuditEntry;
import com.sca4d.core.api.resource.Resource;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.Objects;

/** 保存交接时已编码的审计行及关联资源，供同步、队列和提交后记录共用。 */
@ApiStatus.Internal
public final class FrozenAuditEntry {
    private final EncodedAuditEntry entry;

    private final List<Resource.Ref> resources;

    FrozenAuditEntry(@NotNull EncodedAuditEntry entry, @NotNull List<Resource.Ref> resources) {
        this.entry = Objects.requireNonNull(entry);
        this.resources = List.copyOf(resources);
    }

    EncodedAuditEntry entry() {
        return entry;
    }

    List<Resource.Ref> resources() {
        return resources;
    }
}
