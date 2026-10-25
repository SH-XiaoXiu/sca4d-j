package com.sca4d.iot.query;

import com.sca4d.core.api.error.exception.BusinessException;
import com.sca4d.core.api.query.Page;
import com.sca4d.core.api.query.PageRequest;
import com.sca4d.iot.IotErrorCode;
import com.sca4d.iot.api.history.DeviceHistoryStore;
import com.sca4d.iot.api.message.DeviceAcknowledgement;
import com.sca4d.iot.api.message.DeviceDownlink;
import com.sca4d.iot.api.query.DeviceEvent;
import com.sca4d.iot.api.query.DeviceHistoryQueryService;
import com.sca4d.iot.api.telemetry.TelemetrySample;
import com.sca4d.iot.device.DeviceQuery;
import com.sca4d.iot.device.DeviceRepository;
import com.sca4d.iot.message.DownlinkRepository;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Unmodifiable;
import org.springframework.beans.factory.ObjectProvider;

import java.time.Instant;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/** 从主库读取下行与最新 ACK，并优先从可选历史存储读取完整 ACK、事件和 Telemetry。 */
public class DefaultDeviceHistoryQueryService implements DeviceHistoryQueryService {

    private final @NotNull DeviceRepository devices;

    private final @NotNull DownlinkRepository downlinks;

    private final @NotNull ObjectProvider<DeviceHistoryStore> history;

    public DefaultDeviceHistoryQueryService(
            @NotNull DeviceRepository devices,
            @NotNull DownlinkRepository downlinks,
            @NotNull ObjectProvider<DeviceHistoryStore> history) {
        this.devices = Objects.requireNonNull(devices, "Device Repository 不能为空");
        this.downlinks = Objects.requireNonNull(downlinks, "下行 Repository 不能为空");
        this.history = Objects.requireNonNull(history, "Device 历史存储 Provider 不能为空");
    }

    @Override
    public @NotNull @Unmodifiable Map<@NotNull Long, @NotNull List<@NotNull DeviceDownlink>> recentDownlinks(
            @NotNull Collection<@NotNull Long> deviceIds,
            int limitPerDevice) {
        int checkedLimit = new PageRequest(1, limitPerDevice).verifyMaxSize(100).size();
        List<Long> ids = validateDeviceIds(deviceIds);
        return downlinks.findRecentDownlinks(ids, checkedLimit);
    }

    @Override
    public @NotNull @Unmodifiable Map<@NotNull Long, @NotNull List<@NotNull DeviceEvent>> recentEvents(
            @NotNull Collection<@NotNull Long> deviceIds,
            int limitPerDevice) {
        int checkedLimit = new PageRequest(1, limitPerDevice).verifyMaxSize(100).size();
        List<Long> ids = validateDeviceIds(deviceIds);
        return ids.isEmpty() ? Map.of() : historyStore().recentEvents(ids, checkedLimit);
    }

    @Override
    public @NotNull @Unmodifiable Map<@NotNull Long, @NotNull List<@NotNull DeviceAcknowledgement>> recentAcknowledgements(
            @NotNull Collection<@NotNull Long> messageIds,
            int limitPerMessage) {
        int checkedLimit = new PageRequest(1, limitPerMessage).verifyMaxSize(100).size();
        List<Long> ids = Objects.requireNonNull(messageIds, "下行消息标识不能为空")
                .stream()
                .map(id -> Objects.requireNonNull(id, "下行消息标识不能为空"))
                .distinct()
                .toList();
        for (Long id : ids) {
            if (id <= 0)
                throw new IllegalArgumentException("下行消息标识必须为正数");
        }
        if (ids.isEmpty())
            return Map.of();
        DeviceHistoryStore store = history.getIfAvailable();
        Map<Long, List<DeviceAcknowledgement>> result = new LinkedHashMap<>();
        if (store != null)
            result.putAll(store.recentAcknowledgements(ids, checkedLimit));
        // 每条消息单独判断历史是否缺失，批量补充主库最新投影，不混入已有完整历史。
        List<Long> missing = ids.stream().filter(id -> result.getOrDefault(id, List.of()).isEmpty()).toList();
        result.putAll(downlinks.findLatestAcknowledgements(missing));
        result.replaceAll((id, values) -> List.copyOf(values));
        return Map.copyOf(result);
    }

    /**
     * 校验设备标识为去重后的正数列表，并确认全部 Device 存在。
     *
     * @throws IllegalArgumentException 设备标识为空或不是正数时抛出
     * @throws BusinessException 任一 Device 不存在时以 DEVICE_NOT_FOUND 抛出
     */
    private @NotNull List<Long> validateDeviceIds(@NotNull Collection<@NotNull Long> deviceIds) {
        List<Long> ids = Objects.requireNonNull(deviceIds, "设备标识不能为空")
                .stream()
                .map(id -> Objects.requireNonNull(id, "设备标识不能为空"))
                .distinct()
                .toList();
        for (Long id : ids) {
            if (id <= 0)
                throw new IllegalArgumentException("设备标识必须为正数");
        }
        if (!ids.isEmpty() && devices.find(DeviceQuery.forIds(ids).build()).getItems().size() != ids.size()) {
            throw new BusinessException(IotErrorCode.DEVICE_NOT_FOUND);
        }
        return ids;
    }

    @Override
    public @NotNull Optional<DeviceDownlink> downlink(long messageId) {
        return downlinks.find(messageId);
    }

    @Override
    public @NotNull Page<DeviceAcknowledgement> acknowledgementsByMessageId(
            long messageId,
            @NotNull PageRequest page) {
        PageRequest checked = Objects.requireNonNull(page, "业务回执分页要求不能为空").verifyMaxSize(100);
        DeviceHistoryStore store = history.getIfAvailable();
        if (store != null) {
            Page<DeviceAcknowledgement> historical = store.acknowledgementsByMessageId(messageId, checked);
            if (historical.getTotal() > 0) {
                return historical;
            }
        }
        return downlinks.findAcknowledgementsByMessageId(messageId, checked);
    }

    @Override
    public @NotNull @Unmodifiable Map<@NotNull Long, @NotNull DeviceDownlink> downlinks(
            @NotNull Collection<@NotNull Long> messageIds) {
        return downlinks.findAll(messageIds);
    }

    @Override
    public @NotNull Page<DeviceDownlink> downlinks(long deviceId, @NotNull PageRequest page) {
        validateDeviceId(deviceId);
        return downlinks.findDownlinks(deviceId, page);
    }

    @Override
    public @NotNull Page<DeviceAcknowledgement> acknowledgements(long deviceId, @NotNull PageRequest page) {
        validateDeviceId(deviceId);
        DeviceHistoryStore store = history.getIfAvailable();
        return store == null
                ? downlinks.findAcknowledgements(deviceId, page)
                : store.acknowledgements(deviceId, page);
    }

    @Override
    public @NotNull Page<DeviceEvent> events(long deviceId, @NotNull PageRequest page) {
        validateDeviceId(deviceId);
        return historyStore().events(deviceId, page);
    }

    @Override
    public @NotNull List<TelemetrySample> telemetry(
            long deviceId,
            @NotNull String propertyCode,
            @NotNull Instant from,
            @NotNull Instant to,
            int limit) {
        validateDeviceId(deviceId);
        return historyStore().telemetry(deviceId, propertyCode, from, to, limit);
    }

    /** 要求 Device 存在后再读取对应历史。 */
    private void validateDeviceId(long deviceId) {
        if (devices.find(deviceId).isEmpty()) {
            throw new BusinessException(IotErrorCode.DEVICE_NOT_FOUND);
        }
    }

    /** 要求具体应用已经安装 Device 历史存储。 */
    private @NotNull DeviceHistoryStore historyStore() {
        DeviceHistoryStore store = history.getIfAvailable();
        if (store == null) {
            throw new BusinessException(IotErrorCode.DEPENDENCY_UNAVAILABLE);
        }
        return store;
    }
}
