package com.sca4d.iam.acl;

import com.sca4d.iam.DomainScope;
import org.jetbrains.annotations.CheckReturnValue;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Unmodifiable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/** 按领域 scope 路由 ACL Repository。 */
public class AclRepositoryRegistry {
    private static final Logger LOG = LoggerFactory.getLogger(AclRepositoryRegistry.class);

    private final @NotNull @Unmodifiable Map<@NotNull DomainScope, @NotNull AclRepository> repositories;

    /**
     * 创建注册表并拒绝重复的领域范围。
     *
     * @param repositories 全部 ACL 持久化实现
     */
    public AclRepositoryRegistry(
            @NotNull List<? extends @NotNull AclRepository> repositories) {
        LinkedHashMap<DomainScope, AclRepository> values = new LinkedHashMap<>();
        for (AclRepository repository : Objects.requireNonNull(repositories, "ACL Repository 不能为空")) {
            AclRepository checked = Objects.requireNonNull(repository, "ACL Repository 不能为空");
            if (values.putIfAbsent(checked.scope(), checked) != null) {
                throw new IllegalStateException("ACL Repository scope 重复: " + checked.scope());
            }
        }
        this.repositories = Map.copyOf(values);
        LOG.debug("ACL Repository 已注册 | repositoryCount={}", this.repositories.size());
    }

    /**
     * 返回指定领域唯一的 ACL 持久化实现。
     *
     * @param scope 领域范围
     * @return 已注册实现
     */
    @CheckReturnValue
    public @NotNull AclRepository repository(@NotNull DomainScope scope) {
        AclRepository repository = repositories.get(Objects.requireNonNull(scope, "领域 scope 不能为空"));
        if (repository == null) {
            throw new IllegalArgumentException("ACL Repository 未注册: " + scope);
        }
        LOG.trace(
                "ACL Repository 已路由 | scope={} repository={}",
                scope,
                repository.getClass().getName());
        return repository;
    }

    /**
     * 返回全部已注册实现。
     *
     * @return 不可修改且保持注册顺序的实现列表
     */
    @CheckReturnValue
    public @NotNull @Unmodifiable List<@NotNull AclRepository> repositories() {
        return List.copyOf(repositories.values());
    }
}
