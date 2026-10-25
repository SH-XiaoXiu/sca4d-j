package com.sca4d.iot.state;

import cn.hutool.crypto.digest.DigestUtil;
import com.sca4d.core.api.error.exception.BusinessException;
import com.sca4d.core.api.persistence.AffectedRowCounts;
import com.sca4d.core.api.persistence.ZeroRowPolicy;
import com.sca4d.core.api.retry.Backoff;
import com.sca4d.core.api.retry.RetryContext;
import com.sca4d.core.api.retry.RetryPolicy;
import com.sca4d.core.api.retry.RetryRule;
import com.sca4d.core.spring.persistence.batch.SqlBatches;
import com.sca4d.iot.IotErrorCode;
import com.sca4d.iot.api.query.ReportedStateSnapshot;
import com.sca4d.iot.api.state.ReportedMergeMode;
import com.sca4d.iot.api.state.ReportedStateStore;
import com.sca4d.iot.api.state.ReportedStateUpdate;
import com.sca4d.iot.api.state.ReportedStateWriteResult;
import com.sca4d.iot.model.ThingDataJsonCodec;
import com.sca4d.iot.persistence.EncodedReportedGroup;
import com.sca4d.iot.persistence.EncodedReportedGroupSnapshot;
import com.sca4d.iot.persistence.mapper.ReportedStateMapper;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/** 使用 PostgreSQL version 条件写入维护每个 Device 字段组的 latest reported 投影。 */
public class PostgresqlReportedStateStore implements ReportedStateStore {

    private static final RetryPolicy<Boolean> WRITE_RETRY = RetryPolicy.always(
            RetryRule.atMost(3, Backoff.fixed(Duration.ZERO)));

    private final @NotNull ReportedStateMapper mapper;

    private final @NotNull SqlBatches sqlBatches;

    private final @NotNull ThingDataJsonCodec json;

    /**
     * 创建使用 PostgreSQL 保存 reported 投影的存储。
     *
     * @param mapper reported state SQL Mapper
     * @param json 物模型数据 JSON 编解码器
     * @param sqlBatches SQL 批处理器
     */
    public PostgresqlReportedStateStore(
            @NotNull ReportedStateMapper mapper,
            @NotNull ThingDataJsonCodec json,
            @NotNull SqlBatches sqlBatches) {
        this.mapper = Objects.requireNonNull(mapper, "reported state Mapper 不能为空");
        this.json = Objects.requireNonNull(json, "物模型数据 JSON 编解码器不能为空");
        this.sqlBatches = Objects.requireNonNull(sqlBatches, "SQL 批处理器不能为空");
    }

    /** 同一字段组在事务内完成顺序比较、合并和最多三次 version 条件写入。 */
    @Override
    @Transactional
    public @NotNull ReportedStateWriteResult update(
            long deviceId,
            long bindingGeneration,
            @NotNull ReportedStateUpdate update,
            @NotNull Map<String, Object> normalizedValues,
            @NotNull String orderScope,
            @NotNull String orderValue,
            @NotNull Instant receivedAt) {
        ReportedStateUpdate input = Objects.requireNonNull(update, "reported update 不能为空");
        Map<String, Object> normalized = Map.copyOf(
                Objects.requireNonNull(normalizedValues, "reported 规范值不能为空"));
        Optional<ReportedGroupState> current = findCurrent(deviceId, bindingGeneration, input.groupCode());

        // 条件写入冲突时重新读取最新投影，再执行相同的顺序和内容判断。
        for (long attempt = 1;; attempt++) {
            LinkedHashMap<String, Object> latest = new LinkedHashMap<>();
            if (input.mergeMode() == ReportedMergeMode.PATCH && current.isPresent()) {
                latest.putAll(current.get().values());
            }
            latest.putAll(normalized);
            if (input.mergeMode() == ReportedMergeMode.SNAPSHOT) {
                latest.keySet()
                        .removeIf(
                                code -> input.coveredProperties().contains(code) && !normalized.containsKey(code));
            }
            String hash = DigestUtil.sha256Hex(json.encodeMap(latest));
            long version = 1;
            if (current.isPresent()) {
                ReportedGroupState previous = current.get();
                if (!previous.orderScope().equals(orderScope)) {
                    throw new BusinessException(IotErrorCode.PROTOCOL_CONFLICT);
                }
                int comparison = orderValue.compareTo(previous.orderValue());
                if (comparison < 0) {
                    return new ReportedStateWriteResult(
                            ReportedStateWriteResult.Outcome.OLDER,
                            previous.version());
                }
                if (comparison == 0) {
                    if (!previous.contentHash().equals(hash)) {
                        throw new BusinessException(IotErrorCode.PROTOCOL_CONFLICT);
                    }
                    return new ReportedStateWriteResult(
                            ReportedStateWriteResult.Outcome.DUPLICATE,
                            previous.version());
                }
                version = previous.version() + 1;
            }
            if (save(
                    deviceId,
                    bindingGeneration,
                    input,
                    orderScope,
                    orderValue,
                    hash,
                    latest,
                    receivedAt,
                    current.map(ReportedGroupState::version).orElse(null))) {
                return new ReportedStateWriteResult(ReportedStateWriteResult.Outcome.ACCEPTED, version);
            }
            if (!WRITE_RETRY.evaluate(RetryContext.failed(attempt, false)).retrying()) {
                AffectedRowCounts.requireSingle(
                        0,
                        ZeroRowPolicy.OPTIMISTIC_LOCK,
                        "reported 字段组");
                break;
            }
            current = Optional.of(
                    findCurrent(deviceId, bindingGeneration, input.groupCode())
                            .orElseThrow(() -> new IllegalStateException("并发新增的 reported 字段组无法读取")));
        }
        throw new IllegalStateException("reported 字段组重试没有产生结果");
    }

    /** 读取 Device 当前绑定的全部 reported 字段组。 */
    @Override
    public @NotNull List<ReportedStateSnapshot> find(long deviceId, long bindingGeneration) {
        return mapper.selectReportedGroups(Map.of(deviceId, bindingGeneration), null)
                .stream()
                .map(this::toSnapshot)
                .toList();
    }

    /** 按绑定代数分批读取同一字段组，保持当前状态所属代数不被其他绑定混入。 */
    @Override
    public @NotNull Map<Long, ReportedStateSnapshot> find(
            @NotNull Map<Long, Long> bindingGenerations,
            @NotNull String groupCode) {
        List<Map.Entry<Long, Long>> bindings = List.copyOf(Map.copyOf(bindingGenerations).entrySet());
        Map<Long, ReportedStateSnapshot> snapshots = new LinkedHashMap<>();
        for (EncodedReportedGroupSnapshot snapshot : sqlBatches.query(bindings, 2, 1, batchEntries -> {
            Map<Long, Long> batch = new LinkedHashMap<>();
            for (Map.Entry<Long, Long> binding : batchEntries) {
                batch.put(binding.getKey(), binding.getValue());
            }
            return mapper.selectReportedGroups(batch, groupCode);
        })) {
            snapshots.put(snapshot.deviceId(), toSnapshot(snapshot));
        }
        return Map.copyOf(snapshots);
    }

    /** 删除 Device 指定旧绑定下的全部 PostgreSQL reported 投影。 */
    @Override
    public void delete(long deviceId, long bindingGeneration) {
        mapper.deleteReportedGroups(deviceId, bindingGeneration);
    }

    /** 读取准备比较和条件修改的字段组；不存在时返回空。 */
    private @NotNull Optional<ReportedGroupState> findCurrent(
            long deviceId,
            long bindingGeneration,
            @NotNull String groupCode) {
        EncodedReportedGroup row = mapper.selectReportedGroup(deviceId, bindingGeneration, groupCode);
        return row == null
                ? Optional.empty()
                : Optional.of(
                        new ReportedGroupState(
                                row.orderScope(),
                                row.orderValue(),
                                row.contentHash(),
                                json.decodeMap(row.stateJson()),
                                row.version(),
                                row.observedAt(),
                                row.receivedAt()));
    }

    /** 新增字段组，或按读取到的 version 条件更新字段组。 */
    private boolean save(
            long deviceId,
            long bindingGeneration,
            @NotNull ReportedStateUpdate update,
            @NotNull String orderScope,
            @NotNull String orderValue,
            @NotNull String contentHash,
            @NotNull Map<String, ?> values,
            @NotNull Instant receivedAt,
            @Nullable Long expectedVersion) {
        String stateJson = json.encodeMap(values);
        if (expectedVersion == null) {
            return AffectedRowCounts.changed(
                    mapper.insertReportedGroupIfAbsent(
                            deviceId,
                            bindingGeneration,
                            update.groupCode(),
                            orderScope,
                            orderValue,
                            contentHash,
                            stateJson,
                            update.observedAt(),
                            receivedAt),
                    "新增 reported 字段组");
        }
        return AffectedRowCounts.changed(
                mapper.updateReportedGroup(
                        deviceId,
                        bindingGeneration,
                        update.groupCode(),
                        expectedVersion,
                        orderScope,
                        orderValue,
                        contentHash,
                        stateJson,
                        update.observedAt(),
                        receivedAt),
                "reported 字段组");
    }

    /** 把 reported 查询结果转换成公开快照。 */
    private @NotNull ReportedStateSnapshot toSnapshot(@NotNull EncodedReportedGroupSnapshot row) {
        return new ReportedStateSnapshot(
                row.groupCode(),
                json.decodeMap(row.stateJson()),
                row.version(),
                row.observedAt(),
                row.receivedAt());
    }
}
