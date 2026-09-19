package com.sca4d.openapi.client;

import com.sca4d.core.api.persistence.AffectedRowCounts;
import com.sca4d.core.api.persistence.Patch;
import com.sca4d.core.api.persistence.ZeroRowPolicy;
import com.sca4d.core.spring.persistence.batch.SqlBatches;
import com.sca4d.openapi.client.mapper.OpenApiClientMapper;
import org.jetbrains.annotations.NotNull;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/** 隔离 OpenAPI Client 表和 Operation 关系表的 SQL 与影响行数。 */
public class OpenApiClientRepository {

    private final @NotNull OpenApiClientMapper mapper;

    private final @NotNull SqlBatches sqlBatches;

    /** 创建 OpenAPI Client 持久化边界。 */
    public OpenApiClientRepository(@NotNull OpenApiClientMapper mapper, @NotNull SqlBatches sqlBatches) {
        this.mapper = Objects.requireNonNull(mapper, "OpenAPI Client Mapper 不能为空");
        this.sqlBatches = Objects.requireNonNull(sqlBatches, "SQL 批处理器不能为空");
    }

    /** 原子保存 Client 及完整 Operation 选择，关系写入失败时 Client 也回滚。 */
    @Transactional
    public void create(@NotNull OpenApiClient client, @NotNull Collection<String> operations) {
        OpenApiClient checked = Objects.requireNonNull(client, "OpenAPI Client 不能为空");
        AffectedRowCounts.requireSingle(
                mapper.insertClient(checked.clientId(), checked.accountId(), checked.displayName(), checked.status()),
                ZeroRowPolicy.ERROR,
                "新增 OpenAPI Client");
        replaceOperations(checked.clientId(), operations);
    }

    /** 读取仍存在的 Client。 */
    @Transactional(readOnly = true)
    public @NotNull Optional<OpenApiClient> find(@NotNull String clientId) {
        String checked = requireText(clientId, "Client ID");
        return Optional.ofNullable(mapper.selectClient(checked, null, false));
    }

    /** 按 IAM Account 读取仍存在的 Client。 */
    @Transactional(readOnly = true)
    public @NotNull Optional<OpenApiClient> findByAccountId(long accountId) {
        if (accountId <= 0) {
            throw new IllegalArgumentException("Client Account 标识必须为正数");
        }
        return Optional.ofNullable(mapper.selectClient(null, accountId, false));
    }

    /** 判断 Client 是否开放一个 Operation。 */
    @Transactional(readOnly = true)
    public boolean hasOperation(@NotNull String clientId, @NotNull String operation) {
        return !mapper.selectOperations(requireText(clientId, "Client ID"), requireText(operation, "Operation"))
                .isEmpty();
    }

    /** 读取 Client 当前开放的全部 Operation。 */
    @Transactional(readOnly = true)
    public @NotNull List<@NotNull String> findOperations(@NotNull String clientId) {
        return List.copyOf(mapper.selectOperations(requireText(clientId, "Client ID"), null));
    }

    /** 锁定 Client 后原子替换完整 Operation 列表；空集合清空，失败保留原关系。 */
    @Transactional
    public void replaceOperations(@NotNull String clientId, @NotNull Collection<String> operations) {
        String checkedClientId = requireText(clientId, "Client ID");
        List<String> checkedOperations = Objects.requireNonNull(operations, "Operation 列表不能为空")
                .stream()
                .map(operation -> requireText(operation, "Operation"))
                .toList();
        // 对同一 Client 的关系替换串行提交，避免并发删除和插入产生混合授权。
        Optional.ofNullable(mapper.selectClient(checkedClientId, null, true))
                .orElseThrow(() -> new IllegalArgumentException("OpenAPI Client 不存在"));
        mapper.deleteOperations(checkedClientId);
        sqlBatches.forEach(checkedOperations, 2, 0, batch -> {
            AffectedRowCounts.requireExactly(
                    mapper.insertOperations(checkedClientId, batch),
                    batch.size(),
                    "新增 Client Operation 关系");
        });
    }

    /** 修改 Client 生命周期状态。 */
    @Transactional
    public void updateStatus(@NotNull String clientId, @NotNull OpenApiClientStatus status) {
        AffectedRowCounts.requireSingle(
                mapper.updateStatus(requireText(clientId, "Client ID"), Patch.set(status)),
                ZeroRowPolicy.ERROR,
                "修改 OpenAPI Client 状态");
    }

    /** 规范化存储标识，拒绝空键形成不受约束的查询。 */
    private static String requireText(String value, String name) {
        Objects.requireNonNull(value, name + "不能为空");
        String checked = value.trim();
        if (checked.isEmpty()) {
            throw new IllegalArgumentException(name + "不能为空");
        }
        return checked;
    }
}
