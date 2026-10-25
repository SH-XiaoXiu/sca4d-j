package com.sca4d.iot.timescale.persistence;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sca4d.core.api.persistence.AffectedRows;
import com.sca4d.core.api.persistence.ZeroRowPolicy;
import com.sca4d.core.api.query.Page;
import com.sca4d.core.api.query.PageRequest;
import com.sca4d.core.api.utils.TextUtils;
import com.sca4d.core.spring.persistence.batch.SqlBatches;
import com.sca4d.iot.api.history.DeviceEventMessage;
import com.sca4d.iot.api.message.DeviceAcknowledgement;
import com.sca4d.iot.api.query.DeviceEvent;
import com.sca4d.iot.api.schedule.DeviceScheduleExecution;
import com.sca4d.iot.api.schedule.DeviceScheduleExecutionStatus;
import com.sca4d.iot.api.telemetry.TelemetryBatch;
import com.sca4d.iot.api.telemetry.TelemetrySample;
import com.sca4d.iot.exception.IotTimescaleException;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Unmodifiable;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/** 隐藏 TimescaleDB Mapper、JSON 字段和两张固定 hypertable 的写入形状。 */
public class TimescaleRepository {

    private static final TypeReference<Map<String, Object>> MAP_TYPE = new TypeReference<>() {
    };

    private final @NotNull ObjectMapper json;

    private final @NotNull TimescaleMapper mapper;

    private final @NotNull SqlBatches sqlBatches;

    private final int maxQueryResults;

    /**
     * 批量读取最近记录；每个标识对应有序且不可修改的列表，无记录时为空列表。
     *
     * @param deviceIds 正数标识集合，重复标识合并，空集合不查询数据库
     * @param limitPerDevice 每个标识的记录上限，范围为 1 到 100
     * @return 按标识分组的不可修改结果
     */
    public @NotNull @Unmodifiable Map<@NotNull Long, @NotNull List<@NotNull DeviceEvent>> findRecentEvents(
            @NotNull Collection<@NotNull Long> deviceIds,
            int limitPerDevice) {
        int checkedLimit = new PageRequest(1, limitPerDevice).verifyMaxSize(100).size();
        List<Long> ids = List.copyOf(new LinkedHashSet<>(deviceIds));
        Map<Long, List<DeviceEvent>> result = new LinkedHashMap<>();
        for (Long id : ids) {
            if (id <= 0)
                throw new IllegalArgumentException("历史查询标识必须为正数");
            result.put(id, new ArrayList<>());
        }
        // 限制每批最多 500 个标识，并继续遵守配置的 SQL 参数上限。
        for (int offset = 0; offset < ids.size(); offset += 500) {
            List<Long> chunk = ids.subList(offset, Math.min(offset + 500, ids.size()));
            for (TimescaleThingEvent row : sqlBatches.query(
                    chunk,
                    1,
                    1,
                    batch -> mapper.selectRecentEvents(batch, checkedLimit))) {
                result.get(row.deviceId()).add(toEvent(row));
            }
        }
        result.replaceAll((id, values) -> List.copyOf(values));
        return Map.copyOf(result);
    }

    /**
     * 批量读取最近记录；每个标识对应有序且不可修改的列表，无记录时为空列表。
     *
     * @param messageIds 正数标识集合，重复标识合并，空集合不查询数据库
     * @param limitPerMessage 每个标识的记录上限，范围为 1 到 100
     * @return 按标识分组的不可修改结果
     */
    public @NotNull @Unmodifiable Map<@NotNull Long, @NotNull List<@NotNull DeviceAcknowledgement>> findRecentAcknowledgements(
            @NotNull Collection<@NotNull Long> messageIds,
            int limitPerMessage) {
        int checkedLimit = new PageRequest(1, limitPerMessage).verifyMaxSize(100).size();
        List<Long> ids = List.copyOf(new LinkedHashSet<>(messageIds));
        Map<Long, List<DeviceAcknowledgement>> result = new LinkedHashMap<>();
        for (Long id : ids) {
            if (id <= 0)
                throw new IllegalArgumentException("历史查询标识必须为正数");
            result.put(id, new ArrayList<>());
        }
        // 限制每批最多 500 个标识，并继续遵守配置的 SQL 参数上限。
        for (int offset = 0; offset < ids.size(); offset += 500) {
            List<Long> chunk = ids.subList(offset, Math.min(offset + 500, ids.size()));
            for (TimescaleThingEvent row : sqlBatches.query(
                    chunk,
                    1,
                    1,
                    batch -> mapper.selectRecentAcknowledgements(batch, checkedLimit))) {
                DeviceAcknowledgement acknowledgement = toAcknowledgement(row);
                result.get(acknowledgement.messageId()).add(acknowledgement);
            }
        }
        result.replaceAll((id, values) -> List.copyOf(values));
        return Map.copyOf(result);
    }

    /**
     * 创建只读写 `iot_telemetry_sample` 和 `iot_event` 的 Repository。
     *
     * @param mapper TimescaleDB SQL Mapper
     * @param json 高频历史 JSON 编解码器
     * @param sqlBatches SQL 批处理器
     * @param maxQueryResults 单次 Telemetry 查询最大结果数
     */
    public TimescaleRepository(
            @NotNull TimescaleMapper mapper,
            @NotNull ObjectMapper json,
            @NotNull SqlBatches sqlBatches,
            int maxQueryResults) {
        this.mapper = Objects.requireNonNull(mapper, "Timescale Mapper 不能为空");
        this.json = Objects.requireNonNull(json, "Timescale ObjectMapper 不能为空");
        this.sqlBatches = Objects.requireNonNull(sqlBatches, "SQL 批处理器不能为空");
        if (maxQueryResults < 1) {
            throw new IllegalArgumentException("Timescale 历史查询结果上限必须为正数");
        }
        this.maxQueryResults = maxQueryResults;
    }

    /** 按 Id 和样本顺序幂等写入一批 Telemetry。 */
    public void addTelemetry(long messageId, @NotNull TelemetryBatch batch) {
        TelemetryBatch checked = Objects.requireNonNull(batch, "TelemetryBatch 不能为空");
        List<TimescaleTelemetrySample> samples = new ArrayList<>(checked.samples().size());
        int sampleIndex = 0;
        for (TelemetrySample sample : checked.samples()) {
            samples.add(
                    new TimescaleTelemetrySample(
                            messageId,
                            sampleIndex++,
                            checked.deviceId(),
                            checked.bindingGeneration(),
                            sample.propertyCode(),
                            encode(sample.value()),
                            sample.observedAt(),
                            sample.receivedAt()));
        }
        sqlBatches.forEach(samples, 8, 0, sampleBatch -> {
            int inserted = mapper.insertTelemetrySamples(sampleBatch);
            if (inserted < 0 || inserted > sampleBatch.size()) {
                throw new IllegalStateException("Telemetry 幂等写入返回了非法影响行数");
            }
        });
    }

    /** 按发生时间和 Id 幂等写入一条 Device 事件。 */
    @AffectedRows(target = "新增 Device 事件", whenZero = ZeroRowPolicy.ALLOW)
    public void addEvent(long messageId, @NotNull DeviceEventMessage event) {
        DeviceEventMessage checked = Objects.requireNonNull(event, "DeviceEventMessage 不能为空");
        mapper.insertEvent(messageId, checked, encode(checked.data()));
    }

    /** 查询一个属性在半开时间范围内的 Telemetry。 */
    public @NotNull List<TelemetrySample> findTelemetry(
            long deviceId,
            @NotNull String propertyCode,
            @NotNull Instant from,
            @NotNull Instant to,
            int limit) {
        requireRange(deviceId, from, to, limit);
        return mapper.selectTelemetry(
                deviceId,
                TextUtils.requireText(propertyCode, "Telemetry property code"),
                from,
                to,
                limit)
                .stream()
                .map(
                        row -> new TelemetrySample(
                                row.propertyCode(),
                                decodeValue(row.valueJson()),
                                row.observedAt(),
                                row.receivedAt()))
                .toList();
    }

    /** 分页读取 Device 的全部属性事件和 IoT 状态机事件。 */
    public @NotNull Page<DeviceEvent> findEvents(long deviceId, @NotNull PageRequest page) {
        if (deviceId <= 0) {
            throw new IllegalArgumentException("Device 标识必须为正数");
        }
        PageRequest checked = Objects.requireNonNull(page, "事件分页要求不能为空").verifyMaxSize(100);
        long total = mapper.countEvents(deviceId, null, null);
        List<DeviceEvent> items = total == 0
                ? List.of()
                : mapper.selectEvents(deviceId, null, null, checked.offset(), checked.size())
                        .stream()
                        .map(this::toEvent)
                        .toList();
        return new Page<>(items, checked, total);
    }

    /** 分页读取 Device 每条下行消息的完整 ACK 阶段历史。 */
    public @NotNull Page<DeviceAcknowledgement> findAcknowledgements(
            long deviceId,
            @NotNull PageRequest page) {
        if (deviceId <= 0) {
            throw new IllegalArgumentException("Device 标识必须为正数");
        }
        PageRequest checked = Objects.requireNonNull(page, "业务回执分页要求不能为空").verifyMaxSize(100);
        long total = mapper.countEvents(deviceId, "$downlink.ack", null);
        List<DeviceAcknowledgement> items = total == 0
                ? List.of()
                : mapper.selectEvents(deviceId, "$downlink.ack", null, checked.offset(), checked.size())
                        .stream()
                        .map(this::toAcknowledgement)
                        .toList();
        return new Page<>(items, checked, total);
    }

    /** 分页读取指定下行消息的完整 ACK 阶段历史。 */
    public @NotNull Page<DeviceAcknowledgement> findAcknowledgementsByMessageId(
            long messageId,
            @NotNull PageRequest page) {
        if (messageId <= 0) {
            throw new IllegalArgumentException("下行消息标识必须为正数");
        }
        PageRequest checked = Objects.requireNonNull(page, "业务回执分页要求不能为空").verifyMaxSize(100);
        long total = mapper.countAcknowledgementsByMessageId(messageId);
        List<DeviceAcknowledgement> items = total == 0
                ? List.of()
                : mapper.selectAcknowledgementsByMessageId(messageId, checked.offset(), checked.size())
                        .stream()
                        .map(this::toAcknowledgement)
                        .toList();
        return new Page<>(items, checked, total);
    }

    /** 分页读取一个设备定时规则的完整执行历史。 */
    public @NotNull Page<DeviceScheduleExecution> findScheduleExecutions(
            long scheduleId,
            @NotNull PageRequest page) {
        if (scheduleId <= 0) {
            throw new IllegalArgumentException("设备定时规则标识必须为正数");
        }
        PageRequest checked = Objects.requireNonNull(page, "设备定时执行分页要求不能为空").verifyMaxSize(100);
        long total = mapper.countEvents(null, null, scheduleId);
        List<DeviceScheduleExecution> items = total == 0
                ? List.of()
                : mapper.selectEvents(null, null, scheduleId, checked.offset(), checked.size())
                        .stream()
                        .map(this::toScheduleExecution)
                        .toList();
        return new Page<>(items, checked, total);
    }

    /** 删除早于两个截止时间的完整 TimescaleDB chunk。 */
    public void dropChunks(@NotNull Instant telemetryOlderThan, @NotNull Instant eventOlderThan) {
        mapper.dropTelemetryChunks(Objects.requireNonNull(telemetryOlderThan, "Telemetry 截止时间不能为空"));
        mapper.dropEventChunks(Objects.requireNonNull(eventOlderThan, "事件截止时间不能为空"));
    }

    /** 把事件持久化结果转换成公开查询类型。 */
    private @NotNull DeviceEvent toEvent(@NotNull TimescaleThingEvent row) {
        return new DeviceEvent(
                row.eventId(),
                row.deviceId(),
                row.eventCode(),
                decodeMap(row.outputJson()),
                row.occurredAt(),
                row.receivedAt());
    }

    /** 把 `$downlink.ack` 事件恢复成公开 ACK 类型。 */
    private @NotNull DeviceAcknowledgement toAcknowledgement(@NotNull TimescaleThingEvent row) {
        Map<String, Object> data = decodeMap(row.outputJson());
        String source = text(data, "source");
        Object sourceAcknowledgementId = data.get("sourceAcknowledgementId");
        return new DeviceAcknowledgement(
                number(data, "messageId"),
                text(data, "ackCode"),
                objectMap(data),
                source,
                sourceAcknowledgementId == null ? null : sourceAcknowledgementId.toString(),
                row.occurredAt(),
                row.receivedAt());
    }

    /** 把 `$schedule.execution` 事件恢复成公开定时执行结果。 */
    private @NotNull DeviceScheduleExecution toScheduleExecution(@NotNull TimescaleThingEvent row) {
        Map<String, Object> data = decodeMap(row.outputJson());
        Object messageId = data.get("messageId");
        Object failureCode = data.get("failureCode");
        return new DeviceScheduleExecution(
                row.eventId(),
                number(data, "scheduleId"),
                Instant.parse(text(data, "plannedAt")),
                Instant.parse(text(data, "triggeredAt")),
                DeviceScheduleExecutionStatus.valueOf(text(data, "status")),
                messageId == null ? null : ((Number) messageId).longValue(),
                failureCode == null ? null : failureCode.toString());
    }

    /** 编码一个 JSON 值。 */
    private @NotNull String encode(@NotNull Object value) {
        try {
            return json.writeValueAsString(Objects.requireNonNull(value, "Timescale JSON 值不能为空"));
        } catch (JsonProcessingException exception) {
            throw new IotTimescaleException("Timescale JSON 无法编码", exception);
        }
    }

    /** 解码一个 JSON 值。 */
    private @NotNull Object decodeValue(@NotNull String value) {
        try {
            return json.readValue(value, Object.class);
        } catch (JsonProcessingException exception) {
            throw new IotTimescaleException("Timescale JSON 无法解析", exception);
        }
    }

    /** 解码一个 JSON 对象。 */
    private @NotNull Map<String, Object> decodeMap(@NotNull String value) {
        try {
            return new LinkedHashMap<>(json.readValue(value, MAP_TYPE));
        } catch (JsonProcessingException exception) {
            throw new IotTimescaleException("Timescale JSON 对象无法解析", exception);
        }
    }

    /** 返回事件数据中的必需文本字段。 */
    private static @NotNull String text(@NotNull Map<String, Object> data, @NotNull String name) {
        Object value = data.get(name);
        if (!(value instanceof String result) || result.isBlank()) {
            throw new IotTimescaleException("IoT 事件缺少文本字段: " + name);
        }
        return result;
    }

    /** 返回事件数据中的必需整数。 */
    private static long number(@NotNull Map<String, Object> data, @NotNull String name) {
        Object value = data.get(name);
        if (!(value instanceof Number result)) {
            throw new IotTimescaleException("IoT 事件缺少整数: " + name);
        }
        return result.longValue();
    }

    /** 返回事件数据中的必需对象。 */
    @SuppressWarnings("unchecked")
    private static @NotNull Map<String, Object> objectMap(@NotNull Map<String, Object> data) {
        Object value = data.get("data");
        if (!(value instanceof Map<?, ?> result)) {
            throw new IotTimescaleException("IoT 事件缺少对象: data");
        }
        return Map.copyOf((Map<String, Object>) result);
    }

    /** 校验高频历史查询不会形成无界扫描。 */
    private void requireRange(long deviceId, Instant from, Instant to, int limit) {
        if (deviceId <= 0
                || limit < 1
                || limit > maxQueryResults
                || !Objects.requireNonNull(from, "历史开始时间不能为空")
                        .isBefore(Objects.requireNonNull(to, "历史结束时间不能为空"))) {
            throw new IllegalArgumentException("IoT 高频历史查询范围不合法");
        }
    }

}
