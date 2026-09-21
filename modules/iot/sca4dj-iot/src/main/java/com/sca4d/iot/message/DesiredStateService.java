package com.sca4d.iot.message;

import com.sca4d.core.api.error.BusinessException;
import com.sca4d.core.api.id.IdGenerator;
import com.sca4d.iot.IotErrorCode;
import com.sca4d.iot.api.Device;
import com.sca4d.iot.api.ProductDefinition;
import com.sca4d.iot.api.change.DeviceChangeArea;
import com.sca4d.iot.api.message.DeviceDownlink;
import com.sca4d.iot.api.message.DeviceDownlinkKind;
import com.sca4d.iot.api.message.DeviceDownlinkStatus;
import com.sca4d.iot.api.model.ThingModel;
import com.sca4d.iot.api.model.ThingModelPresentation;
import com.sca4d.iot.api.model.ThingModelRepository;
import com.sca4d.iot.api.model.ThingProperty;
import com.sca4d.iot.api.model.ThingPropertyAccess;
import com.sca4d.iot.change.DeviceChangeNotifier;
import com.sca4d.iot.device.DeviceRepository;
import com.sca4d.iot.model.ThingDataJsonCodec;
import com.sca4d.iot.model.ThingValueValidator;
import com.sca4d.iot.product.ProductDefinitionCatalog;
import org.jetbrains.annotations.CheckReturnValue;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/** 原子修改多个 desired property，并只为实际变化创建新的可靠状态同步消息。 */
public class DesiredStateService {

    private static final Logger LOG = LoggerFactory.getLogger(DesiredStateService.class);

    private static final int DESIRED_CONCURRENCY_RETRIES = 8;

    private final @NotNull DeviceRepository devices;

    private final @NotNull ProductDefinitionCatalog products;

    private final @NotNull DownlinkRepository downlinks;

    private final @NotNull ThingModelRepository models;

    private final @NotNull ThingValueValidator validator;

    private final @NotNull IdGenerator ids;

    private final @NotNull Clock clock;

    private final @NotNull DeviceChangeNotifier changes;

    private final @NotNull ThingDataJsonCodec json;

    private final @NotNull DeviceDeliveryScheduler messagePublisher;

    private final int maxProperties;

    /** 创建 desired state 事务服务。 */
    public DesiredStateService(
            @NotNull DeviceRepository devices,
            @NotNull ProductDefinitionCatalog products,
            @NotNull DownlinkRepository downlinks,
            @NotNull ThingModelRepository models,
            @NotNull ThingValueValidator validator,
            @NotNull IdGenerator ids,
            @NotNull Clock clock,
            @NotNull DeviceChangeNotifier changes,
            @NotNull ThingDataJsonCodec json,
            @NotNull DeviceDeliveryScheduler messagePublisher,
            int maxProperties) {
        this.devices = Objects.requireNonNull(devices, "Device Repository 不能为空");
        this.products = Objects.requireNonNull(products, "ProductDefinition 目录不能为空");
        this.downlinks = Objects.requireNonNull(downlinks, "Downlink Repository 不能为空");
        this.models = Objects.requireNonNull(models, "ThingModelRepository 不能为空");
        this.validator = Objects.requireNonNull(validator, "Thing value 校验器不能为空");
        this.ids = Objects.requireNonNull(ids, "ID 生成器不能为空");
        this.clock = Objects.requireNonNull(clock, "desired 时钟不能为空");
        this.changes = Objects.requireNonNull(changes, "Device 变化通知器不能为空");
        this.json = Objects.requireNonNull(json, "Thing data JSON 编解码器不能为空");
        this.messagePublisher = Objects.requireNonNull(messagePublisher, "下行消息发布器不能为空");
        if (maxProperties < 1) {
            throw new IllegalArgumentException("desired 属性数量上限必须为正数");
        }
        this.maxProperties = maxProperties;
    }

    /**
     * @param deviceId Device 标识
     * @param change 本次明确 set 与 clear 的属性
     * @param acknowledgementTimeout 本次生成消息发送成功后等待 Device 业务 ACK 的时间
     * @return 当前 desiredSequence 和实际生成的消息
     */
    @Transactional
    @CheckReturnValue
    @SuppressWarnings("DuplicatedCode")
    public @NotNull DesiredStateResult change(
            long deviceId,
            @NotNull DesiredStateChange change,
            @NotNull Duration acknowledgementTimeout) {
        // 校验单次修改规模，并解析 Device 当前绑定的 Product 和 ThingModel。
        DesiredStateChange input = Objects.requireNonNull(change, "desired change 不能为空");
        if (input.setValues().size() + input.clearCodes().size() > maxProperties) {
            throw new BusinessException(
                    IotErrorCode.INVALID_INPUT,
                    new IllegalArgumentException("desired 属性数量超过应用配置上限"));
        }
        Duration checkedAcknowledgementTimeout = Objects.requireNonNull(
                acknowledgementTimeout,
                "ACK 等待时间不能为空");
        Device device = devices.find(deviceId)
                .orElseThrow(() -> new BusinessException(IotErrorCode.DEVICE_NOT_FOUND));
        ProductDefinition product = products.definition(device.product());
        ThingModel model = models.find(product.code(), device.thingModelVersion())
                .map(ThingModelPresentation::model)
                .orElseThrow(() -> new BusinessException(IotErrorCode.THING_MODEL_NOT_FOUND));

        // 按 ThingModel 规范校验并规范化全部 set/clear；任一值失败时不修改数据库。
        LinkedHashMap<String, Object> normalized = new LinkedHashMap<>();
        try {
            input.setValues().forEach((code, value) -> {
                ThingProperty property = writableProperty(model, code);
                normalized.put(code, validator.validate(property.valueType(), value));
            });
            input.clearCodes().forEach(code -> writableProperty(model, code));
        } catch (IllegalArgumentException exception) {
            throw new BusinessException(IotErrorCode.INVALID_INPUT, exception);
        }

        // 在并发重试前固定属性定义、消息标识和时间，重试只重新判断数据库中的实际变化。
        LinkedHashMap<String, ThingProperty> properties = new LinkedHashMap<>();
        LinkedHashMap<String, Long> messageIds = new LinkedHashMap<>();
        normalized.keySet().forEach(code -> {
            properties.put(code, writableProperty(model, code));
            messageIds.put(code, ids.nextId().value());
        });
        Instant changedAt = clock.instant();
        DesiredStateResult result = null;
        LinkedHashSet<String> allCodes = new LinkedHashSet<>();
        allCodes.addAll(normalized.keySet());
        allCodes.addAll(input.clearCodes());
        List<String> orderedCodes = allCodes.stream().sorted().toList();

        // 每轮批量读取涉及的属性，并用 desiredSequence 乐观更新争取本次写入权。
        for (int attempt = 0; attempt < DESIRED_CONCURRENCY_RETRIES; attempt++) {
            Long currentDesiredSequence = downlinks.findDesiredSequence(device.id());
            if (currentDesiredSequence == null) {
                throw new IllegalStateException("修改 desired state 时 Device 不存在");
            }
            Map<String, Optional<DesiredPropertyState>> existing = downlinks.findDesiredProperties(
                    device.id(),
                    device.bindingGeneration(),
                    orderedCodes);
            Map<String, Object> changedSet = new LinkedHashMap<>();
            normalized.forEach((code, value) -> {
                DesiredPropertyState current = existing.get(code).orElse(null);
                if (current == null || !json.encodeValue(current.value()).equals(json.encodeValue(value))) {
                    changedSet.put(code, value);
                }
            });
            Set<String> changedClear = input.clearCodes()
                    .stream()
                    .filter(code -> existing.get(code).isPresent())
                    .collect(Collectors.toCollection(LinkedHashSet::new));
            if (changedSet.isEmpty() && changedClear.isEmpty()) {
                result = new DesiredStateResult(currentDesiredSequence, List.of());
                break;
            }
            long desiredSequence = currentDesiredSequence + 1;
            if (!downlinks.advanceDesiredSequence(device.id(), currentDesiredSequence, desiredSequence)) {
                continue;
            }

            // 只为实际变化的 set 创建新消息并终止旧消息；clear 取消旧消息并删除当前值。
            List<DeviceDownlink> messages = new ArrayList<>();
            for (Map.Entry<String, Object> changeEntry : changedSet.entrySet()) {
                String code = changeEntry.getKey();
                ThingProperty property = Objects.requireNonNull(properties.get(code), "desired 属性定义不存在: " + code);
                downlinks.finishPreviousDesiredMessages(
                        device.id(),
                        device.bindingGeneration(),
                        code,
                        DeviceDownlinkStatus.SUPERSEDED,
                        changedAt);
                downlinks.saveDesiredProperty(
                        device.id(),
                        device.bindingGeneration(),
                        code,
                        desiredSequence,
                        changeEntry.getValue());
                DeviceDownlink message = new DeviceDownlink(
                        Objects.requireNonNull(messageIds.get(code), "desired messageId 不存在: " + code),
                        device.id(),
                        device.bindingGeneration(),
                        DeviceDownlinkKind.DESIRED,
                        code,
                        desiredSequence,
                        null,
                        null,
                        Map.of("value", changeEntry.getValue()),
                        property.acknowledgementPolicy().orElseThrow(),
                        checkedAcknowledgementTimeout,
                        property.retryPolicy().orElseThrow(),
                        DeviceDownlinkStatus.PENDING,
                        0,
                        null,
                        changedAt);
                downlinks.add(message);
                messagePublisher.publishDispatch(message.messageId(), 1, null);
                messages.add(message);
            }
            for (String code : changedClear) {
                downlinks.finishPreviousDesiredMessages(
                        device.id(),
                        device.bindingGeneration(),
                        code,
                        DeviceDownlinkStatus.CANCELLED,
                        changedAt);
                downlinks.deleteDesiredProperty(device.id(), device.bindingGeneration(), code);
            }
            result = new DesiredStateResult(desiredSequence, messages);
            break;
        }
        if (result == null) {
            throw new IllegalStateException("desired state 并发修改持续发生，请重试请求");
        }

        // 在同一事务中登记 Device 变化；事务提交后再由通知器发布可见变化。
        changes.changed(
                deviceId,
                List.of(DeviceChangeArea.DESIRED, DeviceChangeArea.DOWNLINK),
                clock.instant());
        LOG.info(
                "Device desired 已修改 | deviceId={} desiredSequence={} setCount={} clearCount={}",
                deviceId,
                result.desiredSequence(),
                result.messages().size(),
                input.clearCodes().size());
        return result;
    }

    /** 返回必须存在且可写的属性。 */
    private static ThingProperty writableProperty(ThingModel model, String code) {
        ThingProperty property = model.properties().get(code);
        if (property == null || property.access() != ThingPropertyAccess.READ_WRITE) {
            throw new IllegalArgumentException("属性不存在或不可写: " + code);
        }
        return property;
    }
}
