package com.sca4d.iam.base.acl;

import com.sca4d.core.api.permission.Action;
import com.sca4d.core.api.persistence.AffectedRows;
import com.sca4d.core.api.persistence.ZeroRowPolicy;
import com.sca4d.iam.DomainScope;
import com.sca4d.iam.acl.AclEntry;
import com.sca4d.iam.acl.AclRepository;
import com.sca4d.iam.base.mapper.PlatformAclMapper;
import org.jetbrains.annotations.CheckReturnValue;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Unmodifiable;

import java.util.List;
import java.util.Objects;
import java.util.Set;

/** Platform 表实现的内置 ACL Repository。 */
public class PlatformAclRepository implements AclRepository {
    /** Platform ACL 的领域范围。 */
    public static final @NotNull DomainScope PLATFORM = new DomainScope("PLATFORM");

    /** 内置 Platform ACL 示例认领的资源类型。 */
    public static final @NotNull String RESOURCE_TYPE = "platform-resource";

    private final @NotNull PlatformAclMapper mapper;

    /**
     * 创建 Platform ACL 持久化实现。
     *
     * @param mapper Platform ACL Mapper
     */
    public PlatformAclRepository(@NotNull PlatformAclMapper mapper) {
        this.mapper = Objects.requireNonNull(mapper, "Platform ACL Mapper 不能为空");
    }

    /** {@inheritDoc} */
    @Override
    @CheckReturnValue
    public @NotNull DomainScope scope() {
        return PLATFORM;
    }

    /** {@inheritDoc} */
    @Override
    @CheckReturnValue
    public @NotNull @Unmodifiable Set<@NotNull String> resourceTypes() {
        return Set.of(RESOURCE_TYPE);
    }

    /** {@inheritDoc} */
    @Override
    @AffectedRows(target = "Platform ACL", whenZero = ZeroRowPolicy.ALLOW)
    public void grant(
            @NotNull Long principalId,
            @NotNull String type,
            @NotNull String id,
            @NotNull Action action) {
        mapper.insert(principalId, type, id, action);
    }

    /** {@inheritDoc} */
    @Override
    @AffectedRows(target = "Platform ACL", whenZero = ZeroRowPolicy.ALLOW)
    public void revoke(
            @NotNull Long principalId,
            @NotNull String type,
            @NotNull String id,
            @NotNull Action action) {
        AclEntry checked = new AclEntry(principalId, type, id, action);
        mapper.delete(checked.principalId(), checked.resourceType(), checked.resourceId(), checked.action());
    }

    /** {@inheritDoc} */
    @Override
    @CheckReturnValue
    public boolean allows(
            @NotNull Long principalId,
            @NotNull String type,
            @NotNull String id,
            @NotNull Action action) {
        AclEntry checked = new AclEntry(principalId, type, id, action);
        return !mapper
                .selectEntries(checked.principalId(), checked.resourceType(), checked.resourceId(), checked.action(), 1)
                .isEmpty();
    }

    /** {@inheritDoc} */
    @Override
    @CheckReturnValue
    public @NotNull @Unmodifiable List<@NotNull String> findAllowedResourceIds(
            @NotNull Long principalId,
            @NotNull String type,
            @NotNull Action action) {
        return mapper.selectEntries(principalId, type, null, Objects.requireNonNull(action, "ACL 动作不能为空"), null)
                .stream()
                .map(AclEntry::resourceId)
                .toList();
    }

    /** {@inheritDoc} */
    @Override
    @CheckReturnValue
    public @NotNull @Unmodifiable List<@NotNull AclEntry> findEntries(
            @NotNull Long principalId,
            @NotNull String type) {
        return List.copyOf(mapper.selectEntries(principalId, type, null, null, null));
    }

    /** {@inheritDoc} */
    @Override
    public void revokeByPrincipal(@NotNull Long principalId) {
        mapper.delete(Objects.requireNonNull(principalId, "主体标识不能为空"), null, null, null);
    }

    /** {@inheritDoc} */
    @Override
    public void revokeByResource(@NotNull String type, @NotNull String id) {
        mapper.delete(
                null,
                Objects.requireNonNull(type, "资源类型不能为空"),
                Objects.requireNonNull(id, "资源标识不能为空"),
                null);
    }
}
