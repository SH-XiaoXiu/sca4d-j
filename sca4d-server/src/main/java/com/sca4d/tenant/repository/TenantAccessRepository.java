package com.sca4d.tenant.repository;

import static com.mybatisflex.core.query.QueryMethods.column;

import com.mybatisflex.core.query.QueryWrapper;
import com.sca4d.common.access.AccessAction;
import com.sca4d.common.id.IdGenerator;
import com.sca4d.entity.TenantAccessGrantEntity;
import com.sca4d.mapper.TenantAccessMapper;
import com.sca4d.tenant.policy.TenantAccessGrant;
import lombok.RequiredArgsConstructor;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.springframework.stereotype.Repository;

import java.util.ArrayList;
import java.util.List;

/** 读写 PLATFORM Principal 对租户的 ACL 记录。 */
@Repository
@RequiredArgsConstructor
public class TenantAccessRepository {

    private final TenantAccessMapper mapper;
    private final IdGenerator idGenerator;

    /**
     * 一次读取一个 Principal 的全部租户访问规则。
     *
     * @param principalId PLATFORM Principal 主键
     * @return 默认规则在前、具体租户规则按租户主键排序的不可变集合
     */
    public @NotNull List<TenantAccessGrant> findGrants(@NotNull Long principalId) {
        QueryWrapper query = QueryWrapper.create()
                .select(
                        column(TenantAccessGrantEntity::getTenantId),
                        column(TenantAccessGrantEntity::getAccessAction))
                .from(TenantAccessGrantEntity.class)
                .where(TenantAccessGrantEntity::getPrincipalId)
                .eq(principalId)
                .orderBy(TenantAccessGrantEntity::getTenantId)
                .asc();
        return mapper.selectListByQueryAs(query, TenantAccessGrantEntity.class).stream()
                .map(grant -> new TenantAccessGrant(grant.getTenantId(), grant.getAccessAction()))
                .toList();
    }

    /**
     * 用一个完整快照替换 Principal 的租户 ACL。
     *
     * <p>固定执行一次删除，非空快照再执行一次批量插入；调用方事务与 Principal 乐观锁共同保证原子性。
     *
     * @param principalId PLATFORM Principal 主键
     * @param defaultAction 全部租户默认等级；空值表示无默认规则
     * @param grants 具体租户规则
     */
    public void replace(
            @NotNull Long principalId,
            @Nullable AccessAction defaultAction,
            @NotNull List<TenantAccessGrant> grants) {
        mapper.deleteByQuery(QueryWrapper.create()
                .from(TenantAccessGrantEntity.class)
                .where(TenantAccessGrantEntity::getPrincipalId)
                .eq(principalId));
        List<TenantAccessGrantEntity> entities = new ArrayList<>();
        if (defaultAction != null) {
            entities.add(new TenantAccessGrantEntity()
                    .setId(idGenerator.nextId())
                    .setPrincipalId(principalId)
                    .setAccessAction(defaultAction));
        }
        for (TenantAccessGrant grant : grants) {
            entities.add(new TenantAccessGrantEntity()
                    .setId(idGenerator.nextId())
                    .setPrincipalId(principalId)
                    .setTenantId(grant.getTenantId())
                    .setAccessAction(grant.getAccessAction()));
        }
        if (!entities.isEmpty() && mapper.insertGrants(entities) != entities.size()) {
            throw new IllegalStateException("租户访问规则写入行数异常");
        }
    }

    /** 为新 PLATFORM Principal 建立全部租户 MANAGE 默认规则。 */
    public void grantAll(@NotNull Long principalId) {
        replace(principalId, AccessAction.MANAGE, List.of());
    }

    /** 删除 Subject 清理前仍保留的全部 Principal ACL；空集合执行零条 SQL。 */
    public void deleteByPrincipalIds(@NotNull List<Long> principalIds) {
        if (!principalIds.isEmpty()) {
            mapper.deleteByQuery(QueryWrapper.create()
                    .from(TenantAccessGrantEntity.class)
                    .where(TenantAccessGrantEntity::getPrincipalId)
                    .in(principalIds));
        }
    }

    /** 删除已经进入终态的具体租户覆盖规则；全部租户默认规则继续保留。 */
    public void deleteByTenant(@NotNull Long tenantId) {
        mapper.deleteByQuery(QueryWrapper.create()
                .from(TenantAccessGrantEntity.class)
                .where(TenantAccessGrantEntity::getTenantId)
                .eq(tenantId));
    }
}
