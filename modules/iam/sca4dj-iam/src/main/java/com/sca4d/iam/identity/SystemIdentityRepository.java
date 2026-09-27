package com.sca4d.iam.identity;

import com.sca4d.core.api.identity.Subject;
import com.sca4d.core.api.identity.SubjectType;
import com.sca4d.core.api.identity.SystemPrincipal;
import com.sca4d.core.api.name.code.TreeCode;
import com.sca4d.core.api.persistence.AffectedRowCounts;
import com.sca4d.core.api.persistence.ZeroRowPolicy;
import com.sca4d.core.spring.persistence.batch.SqlBatches;
import com.sca4d.iam.identity.mapper.IdentityMapper;
import com.sca4d.iam.identity.mapper.SystemIdentityMapper;
import org.jetbrains.annotations.NotNull;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.Collection;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/** 原子维护 SYSTEM 的固定身份绑定，拒绝接管已有 ID 或改写历史映射。 */
public class SystemIdentityRepository {

    private static final PrincipalType TYPE = new PrincipalType(SystemPrincipalCodec.TYPE);

    private final @NotNull SystemIdentityMapper bindings;

    private final @NotNull IdentityMapper identities;

    private final @NotNull SqlBatches batches;

    /**
     * @param bindings 系统 Principal 绑定 Mapper
     * @param identities Subject 和 Principal Mapper
     * @param batches 批量查询与写入工具
     */
    public SystemIdentityRepository(
            @NotNull SystemIdentityMapper bindings,
            @NotNull IdentityMapper identities,
            @NotNull SqlBatches batches) {
        this.bindings = Objects.requireNonNull(bindings);
        this.identities = Objects.requireNonNull(identities);
        this.batches = Objects.requireNonNull(batches);
    }

    /**
     * 登记并校验配置中的固定系统身份。
     *
     * @param configured 系统身份编码与固定 Principal ID
     * @return 已从数据库确认的系统身份表
     * @throws IllegalStateException 不在事务内、保留 Subject 冲突、绑定被改写或状态异常时抛出
     */
    public @NotNull Map<TreeCode, SystemPrincipal> register(@NotNull Map<TreeCode, Long> configured) {
        if (!TransactionSynchronizationManager.isActualTransactionActive()) {
            throw new IllegalStateException("系统身份登记必须在事务内完成");
        }
        // 复用身份 Mapper 创建保留 Subject，已有记录只校验，禁止覆盖其类型和状态。
        try {
            AffectedRowCounts.requireSingle(
                    identities.insertSubjects(List.of(Subject.SYSTEM), IdentityStatus.ACTIVE),
                    ZeroRowPolicy.ALLOW,
                    "登记 SYSTEM Subject");
        } catch (DuplicateKeyException exception) {
            throw new IllegalStateException("已有 SYSTEM Subject 与配置 ID 冲突，请人工迁移后重启", exception);
        }
        Subject subject = identities.selectSubject(Subject.SYSTEM.subjectId(), IdentityStatus.ACTIVE);
        if (subject == null || subject.type() != SubjectType.SYSTEM) {
            throw new IllegalStateException("SYSTEM Subject 的配置 ID 已被占用或状态异常，请人工迁移后重启");
        }

        // 一个集合 SQL 只绑定本次实际创建的 Principal，唯一约束协调并发实例，不需要全局锁。
        List<SystemPrincipal> requested = configured.entrySet()
                .stream()
                .map(entry -> new SystemPrincipal(entry.getValue(), entry.getKey()))
                .sorted(Comparator.comparingLong(SystemPrincipal::principalId))
                .toList();
        batches.forEach(requested, 2, 3, batch -> {
            int inserted = bindings.insertBindings(batch, subject.subjectId(), TYPE, IdentityStatus.ACTIVE);
            AffectedRowCounts.requireAtLeast(inserted, 0, "登记系统身份绑定");
            if (inserted > batch.size()) {
                throw new IllegalStateException("系统身份绑定返回非法写入数量");
            }
        });

        // 批量读取实际绑定和 Principal 状态；冲突、换 ID、停用或删除都拒绝启动。
        Collection<TreeCode> codes = configured.keySet();
        Map<TreeCode, SystemPrincipal> confirmed = batches.query(codes, 1, 0, bindings::selectBindings)
                .stream()
                .collect(Collectors.toMap(SystemPrincipal::code, principal -> principal, (left, right) -> {
                    throw new IllegalStateException("系统身份编码存在重复绑定");
                }, LinkedHashMap::new));
        Set<Long> active = new HashSet<>(
                batches.query(
                        configured.values(),
                        1,
                        2,
                        batch -> identities.selectPrincipalIds(batch, TYPE, IdentityStatus.ACTIVE, null)));
        for (SystemPrincipal expected : requested) {
            SystemPrincipal actual = confirmed.get(expected.code());
            if (actual == null) {
                throw new IllegalStateException(
                        "系统身份 " + expected.code() + " 的 ID " + expected.principalId() + " 已被占用，请配置其他 ID");
            }
            if (actual.principalId() != expected.principalId()) {
                throw new IllegalStateException(
                        "系统身份 " + expected.code() + " 历史 ID 为 " + actual.principalId()
                                + "，配置 ID 为 " + expected.principalId() + "；禁止自动修改，请人工迁移数据库后重启");
            }
            if (!active.contains(actual.principalId())) {
                throw new IllegalStateException("系统身份 " + actual.code() + " 的 Principal 状态异常，请人工检查数据库");
            }
        }
        return Map.copyOf(confirmed);
    }
}
