package com.sca4d.iam.rbac;

import org.jetbrains.annotations.CheckReturnValue;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Unmodifiable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/** 按 {@link RbacDomain} 查找应用中负责对应业务角色数据的 {@link RbacRepository}。 */
public final class RbacRepositoryRegistry {

    private static final Logger LOG = LoggerFactory.getLogger(RbacRepositoryRegistry.class);

    private final @NotNull @Unmodifiable Map<@NotNull RbacDomain, @NotNull RbacRepository> repositories;

    /**
     * 收集 Repository，并在启动时拒绝重复 RBAC 领域。
     *
     * @param repositories 应用安装的 RBAC Repository
     */
    public RbacRepositoryRegistry(@NotNull Collection<? extends @NotNull RbacRepository> repositories) {
        Map<RbacDomain, RbacRepository> indexed = new LinkedHashMap<>();
        for (RbacRepository repository : Objects.requireNonNull(repositories, "RBAC Repository 集合不能为空")) {
            RbacRepository checked = Objects.requireNonNull(repository, "RBAC Repository 不能为空");
            RbacDomain domain = Objects.requireNonNull(checked.domain(), "RBAC Repository 领域不能为空");
            if (indexed.putIfAbsent(domain, checked) != null) {
                throw new IllegalStateException("多个 RbacRepository 声明了相同领域: " + domain);
            }
        }
        this.repositories = Map.copyOf(indexed);
        LOG.debug("RBAC Repository 注册完成 | repositoryCount={}", this.repositories.size());
    }

    /**
     * 取得负责指定领域的 Repository。
     *
     * @param domain RBAC 领域
     * @return 唯一 Repository
     * @throws IllegalArgumentException 应用没有安装该领域时抛出
     */
    @CheckReturnValue
    public @NotNull RbacRepository repository(@NotNull RbacDomain domain) {
        RbacDomain checked = Objects.requireNonNull(domain, "RBAC 领域不能为空");
        RbacRepository repository = repositories.get(checked);
        if (repository == null) {
            throw new IllegalArgumentException("应用没有安装对应的 RbacRepository: " + checked);
        }
        LOG.trace(
                "RBAC Repository 路由完成 | domain={} repositoryType={}",
                checked,
                repository.getClass().getName());
        return repository;
    }
}
