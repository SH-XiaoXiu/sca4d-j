package com.sca4d.iot.state;

import static com.sca4d.core.api.name.NameFormat.COLON;

import cn.hutool.crypto.digest.DigestUtil;
import com.sca4d.core.api.error.BusinessException;
import com.sca4d.core.api.event.Event;
import com.sca4d.core.api.id.IdGenerator;
import com.sca4d.core.api.messaging.DispatchPolicy;
import com.sca4d.core.api.messaging.MessageBus;
import com.sca4d.core.api.messaging.MessageId;
import com.sca4d.iot.IotErrorCode;
import com.sca4d.iot.api.Device;
import com.sca4d.iot.api.ProductDefinition;
import com.sca4d.iot.api.change.DeviceChangeArea;
import com.sca4d.iot.api.history.DeviceEventMessage;
import com.sca4d.iot.api.message.DeviceAcknowledgement;
import com.sca4d.iot.api.message.IotMessageTypes;
import com.sca4d.iot.api.model.ThingEvent;
import com.sca4d.iot.api.model.ThingEventDelivery;
import com.sca4d.iot.api.model.ThingModel;
import com.sca4d.iot.api.model.ThingModelPresentation;
import com.sca4d.iot.api.model.ThingModelRepository;
import com.sca4d.iot.api.model.ThingProperty;
import com.sca4d.iot.api.state.ReportedStateStore;
import com.sca4d.iot.api.state.ReportedStateUpdate;
import com.sca4d.iot.api.state.ReportedStateWriteResult;
import com.sca4d.iot.api.state.ThingEventOccurrence;
import com.sca4d.iot.api.telemetry.TelemetryBatch;
import com.sca4d.iot.api.telemetry.TelemetrySample;
import com.sca4d.iot.change.DeviceChangeNotifier;
import com.sca4d.iot.device.DeviceRepository;
import com.sca4d.iot.message.DesiredConvergence;
import com.sca4d.iot.message.DownlinkRepository;
import com.sca4d.iot.message.policy.ReportedEqualsDesiredAcknowledgementPolicy;
import com.sca4d.iot.model.ThingValueValidator;
import com.sca4d.iot.product.ProductDefinitionCatalog;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

/** 校验 Device 上报，更新 latest reported，收敛 ACK，并发布 Telemetry 和事件消息。 */
public class ReportedStateService {

    private static final Logger LOG = LoggerFactory.getLogger(ReportedStateService.class);

    private final @NotNull DeviceRepository devices;

    private final @NotNull ProductDefinitionCatalog products;

    private final @NotNull ReportedStateStore reportedStates;

    private final @NotNull DownlinkRepository downlinks;

    private final @NotNull ThingModelRepository models;

    private final @NotNull ThingValueValidator validator;

    private final @NotNull IdGenerator ids;

    private final @NotNull MessageBus messages;

    private final @NotNull Clock clock;

    private final @NotNull DeviceChangeNotifier changes;

    private final int maxReportedProperties;

    private final int maxEventsPerMessage;

    /** 创建 reported 主事务服务。 */
    @SuppressWarnings("ParameterNumber")
    public ReportedStateService(
            @NotNull DeviceRepository devices,
            @NotNull ProductDefinitionCatalog products,
            @NotNull ReportedStateStore reportedStates,
            @NotNull DownlinkRepository downlinks,
            @NotNull ThingModelRepository models,
            @NotNull ThingValueValidator validator,
            @NotNull IdGenerator ids,
            @NotNull MessageBus messages,
            @NotNull Clock clock,
            @NotNull DeviceChangeNotifier changes,
            int maxReportedProperties,
            int maxEventsPerMessage) {
        this.devices = Objects.requireNonNull(devices, "Device Repository 不能为空");
        this.products = Objects.requireNonNull(products, "ProductDefinition 目录不能为空");
        this.reportedStates = Objects.requireNonNull(reportedStates, "ReportedStateStore 不能为空");
        this.downlinks = Objects.requireNonNull(downlinks, "Downlink Repository 不能为空");
        this.models = Objects.requireNonNull(models, "ThingModelRepository 不能为空");
        this.validator = Objects.requireNonNull(validator, "Thing value 校验器不能为空");
        this.ids = Objects.requireNonNull(ids, "reported 顺序 ID 生成器不能为空");
        this.messages = Objects.requireNonNull(messages, "MessageBus 不能为空");
        this.clock = Objects.requireNonNull(clock, "reported 时钟不能为空");
        this.changes = Objects.requireNonNull(changes, "Device 变化通知器不能为空");
        if (maxReportedProperties < 1 || maxEventsPerMessage < 1) {
            throw new IllegalArgumentException("reported 属性和事件数量上限必须为正数");
        }
        this.maxReportedProperties = maxReportedProperties;
        this.maxEventsPerMessage = maxEventsPerMessage;
    }

    /**
     * @param device 连接路由已经读取的完整 Device
     * @param update 一个字段组的 reported 更新
     * @param events 同一协议报文解析出的离散事件
     * @return latest reported 的处理结果
     */
    @Transactional
    @SuppressWarnings("DuplicatedCode")
    public @NotNull ReportedStateResult accept(
            @NotNull Device device,
            @NotNull ReportedStateUpdate update,
            @NotNull List<ThingEventOccurrence> events) {
        ReportedStateUpdate input = Objects.requireNonNull(update, "reported update 不能为空");
        Device checkedDevice = Objects.requireNonNull(device, "Device 不能为空");
        List<ThingEventOccurrence> checkedEvents = List.copyOf(Objects.requireNonNull(events, "reported 事件不能为空"));

        // 在任何模型和状态 IO 前拒绝超限报文，并确认路由读取的 Device 绑定尚未切换。
        if (input.coveredProperties().size() > maxReportedProperties
                || checkedEvents.size() > maxEventsPerMessage) {
            throw new BusinessException(
                    IotErrorCode.INVALID_INPUT,
                    new IllegalArgumentException("reported 属性或事件数量超过应用配置上限"));
        }
        if (!devices.isCurrentBinding(checkedDevice.id(), checkedDevice.bindingGeneration())) {
            throw new BusinessException(IotErrorCode.STATE_CONFLICT);
        }

        // 使用同一 Device 快照解析 Product 和物模型，再把上报值转换成物模型声明的精确类型。
        ProductDefinition product = products.definition(checkedDevice.product());
        ThingModel model = models.find(product.code(), checkedDevice.thingModelVersion())
                .map(ThingModelPresentation::model)
                .orElseThrow(() -> new BusinessException(IotErrorCode.THING_MODEL_NOT_FOUND));
        Instant receivedAt = clock.instant();

        LinkedHashMap<String, Object> normalized = validateReported(model, input);
        String requestedOrderScope = input.orderScope();
        String orderScope = requestedOrderScope == null ? "platform:" + input.groupCode() : requestedOrderScope;
        String requestedOrderValue = input.orderValue();
        String orderValue = requestedOrderValue == null
                ? String.format(Locale.ROOT, "%019d", ids.nextId().value())
                : requestedOrderValue;

        // 原子更新 latest reported；只有未过期结果才批量收敛 desired，实际变化才发布 Telemetry。
        ReportedStateWriteResult write = reportedStates.update(
                checkedDevice.id(),
                checkedDevice.bindingGeneration(),
                input,
                normalized,
                orderScope,
                orderValue,
                receivedAt);
        if (write.outcome() != ReportedStateWriteResult.Outcome.OLDER) {
            convergeDesired(checkedDevice, normalized, input, orderScope, orderValue, receivedAt);
        }
        if (write.outcome() == ReportedStateWriteResult.Outcome.ACCEPTED) {
            publishTelemetry(checkedDevice, model, normalized, input.observedAt(), receivedAt);
        }

        // 校验并发布同一报文携带的离散事件，最后按实际变化区域登记一次 Device 变化通知。
        boolean eventsChanged = publishEvents(checkedDevice, model, checkedEvents, receivedAt);
        EnumSet<DeviceChangeArea> changedAreas = EnumSet.noneOf(DeviceChangeArea.class);
        if (write.outcome() == ReportedStateWriteResult.Outcome.ACCEPTED) {
            changedAreas.add(DeviceChangeArea.REPORTED);
        }
        if (eventsChanged) {
            changedAreas.add(DeviceChangeArea.EVENT);
        }
        if (!changedAreas.isEmpty()) {
            changes.changed(checkedDevice.id(), changedAreas, receivedAt);
        }
        LOG.debug(
                "Device reported 已处理 | deviceId={} group={} outcome={} version={}",
                checkedDevice.id(),
                input.groupCode(),
                write.outcome(),
                write.version());
        return new ReportedStateResult(
                ReportedStateResult.Outcome.valueOf(write.outcome().name()),
                write.version());
    }

    /** 校验字段组覆盖范围和值，并转换成物模型精确 Java 类型。 */
    private LinkedHashMap<String, Object> validateReported(ThingModel model, ReportedStateUpdate input) {
        LinkedHashMap<String, Object> normalized = new LinkedHashMap<>();
        try {
            for (String code : input.coveredProperties()) {
                if (!model.properties().containsKey(code)) {
                    throw new IllegalArgumentException("reported 覆盖未知属性: " + code);
                }
            }
        } catch (IllegalArgumentException exception) {
            throw new BusinessException(IotErrorCode.INVALID_INPUT, exception);
        }
        input.values().forEach((code, value) -> {
            ThingProperty property = model.properties().get(code);
            if (property == null) {
                throw new BusinessException(
                        IotErrorCode.INVALID_INPUT,
                        new IllegalArgumentException("reported 包含未知属性: " + code));
            }
            try {
                normalized.put(code, validator.validate(property.valueType(), value));
            } catch (IllegalArgumentException exception) {
                throw new BusinessException(
                        IotErrorCode.INVALID_INPUT,
                        new IllegalArgumentException("reported 属性值不符合物模型: " + code, exception));
            }
        });
        return normalized;
    }

    /** 为 reported 与 current desired 相等的属性生成统一 ACK。 */
    private void convergeDesired(
            Device device,
            Map<String, Object> normalized,
            ReportedStateUpdate input,
            String orderScope,
            String orderValue,
            Instant receivedAt) {
        for (DesiredConvergence convergence : downlinks.convergeDesired(
                device.id(),
                device.bindingGeneration(),
                normalized,
                receivedAt)) {
            String sourceAcknowledgementId = input.groupCode()
                    + ':' + orderScope + ':' + orderValue + ':' + convergence.propertyCode();
            DeviceAcknowledgement acknowledgement = new DeviceAcknowledgement(
                    convergence.messageId(),
                    ReportedEqualsDesiredAcknowledgementPolicy.CODE.format(COLON),
                    Map.of("propertyCode", convergence.propertyCode()),
                    "REPORTED",
                    sourceAcknowledgementId,
                    input.observedAt(),
                    receivedAt);
            MessageId messageId = messages.dispatch(
                    acknowledgement,
                    DispatchPolicy.outbox()).messageId();
            LOG.trace(
                    "reported 推导 ACK 已登记 | deviceId={} downlinkMessageId={} outboxMessageId={}",
                    device.id(),
                    convergence.messageId(),
                    messageId.value());
        }
    }

    /** 把声明为 Telemetry 的本次 reported 值在主事务提交后发布。 */
    private void publishTelemetry(
            Device device,
            ThingModel model,
            Map<String, Object> normalized,
            Instant observedAt,
            Instant receivedAt) {
        List<TelemetrySample> samples = normalized.entrySet()
                .stream()
                .filter(entry -> model.properties().get(entry.getKey()).telemetry())
                .map(entry -> new TelemetrySample(entry.getKey(), entry.getValue(), observedAt, receivedAt))
                .toList();
        if (samples.isEmpty()) {
            return;
        }
        TelemetryBatch batch = new TelemetryBatch(device.id(), device.bindingGeneration(), samples);
        MessageId telemetryMessageId = messages.dispatch(
                new Event<>(
                        IotMessageTypes.TELEMETRY_REPORTED,
                        null,
                        receivedAt,
                        batch),
                DispatchPolicy.afterCommit()).messageId();
        LOG.trace(
                "Device Telemetry 提交后消息已登记 | deviceId={} sampleCount={} telemetryMessageId={}",
                device.id(),
                samples.size(),
                telemetryMessageId);
    }

    /** 校验离散事件，并按物模型声明选择提交后或 Outbox 可靠发布。 */
    private boolean publishEvents(
            Device device,
            ThingModel model,
            List<ThingEventOccurrence> events,
            Instant receivedAt) {
        boolean changed = false;
        for (ThingEventOccurrence occurrence : Objects.requireNonNull(events, "ThingEvent 列表不能为空")) {
            ThingEvent event = model.events().get(occurrence.eventCode());
            if (event == null || !event.outputs().keySet().equals(occurrence.outputs().keySet())) {
                throw new BusinessException(IotErrorCode.INVALID_INPUT);
            }
            LinkedHashMap<String, Object> outputs = new LinkedHashMap<>();
            try {
                event.outputs()
                        .forEach(
                                (code, type) -> outputs.put(
                                        code,
                                        validator.validate(type, occurrence.outputs().get(code))));
            } catch (IllegalArgumentException exception) {
                throw new BusinessException(IotErrorCode.INVALID_INPUT, exception);
            }
            Instant occurredAt = occurrence.occurredAt().orElse(receivedAt);
            if (event.delivery() == ThingEventDelivery.RELIABLE
                    && (occurrence.sourceEventId().isEmpty() || occurrence.occurredAt().isEmpty())) {
                throw new BusinessException(IotErrorCode.INVALID_INPUT);
            }
            DeviceEventMessage payload = new DeviceEventMessage(
                    device.id(),
                    device.bindingGeneration(),
                    occurrence.eventCode(),
                    occurrence.sourceEventId().orElse(null),
                    outputs,
                    occurredAt,
                    occurrence.occurredAt().isPresent(),
                    receivedAt);
            String businessKey = occurrence.sourceEventId()
                    .map(
                            sourceId -> "iot-event:" + DigestUtil.sha256Hex(
                                    device.id() + ":" + device.bindingGeneration() + ":"
                                            + occurrence.eventCode() + ":" + sourceId + ":" + occurredAt))
                    .orElse(null);
            Event<DeviceEventMessage> message = new Event<>(
                    IotMessageTypes.DEVICE_EVENT_OCCURRED,
                    businessKey,
                    occurredAt,
                    payload);
            DispatchPolicy policy = event.delivery() == ThingEventDelivery.RELIABLE
                    ? DispatchPolicy.outbox()
                    : DispatchPolicy.afterCommit();
            MessageId eventMessageId = messages.dispatch(message, policy).messageId();
            LOG.trace(
                    "Device 事件消息已登记 | deviceId={} eventCode={} delivery={} eventMessageId={}",
                    device.id(),
                    occurrence.eventCode(),
                    event.delivery(),
                    eventMessageId);
            changed = true;
        }
        return changed;
    }
}
