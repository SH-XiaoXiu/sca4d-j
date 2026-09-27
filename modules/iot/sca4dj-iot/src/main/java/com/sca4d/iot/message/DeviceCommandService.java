package com.sca4d.iot.message;

import com.sca4d.core.api.data.DataKey;
import com.sca4d.core.api.data.DataValues;
import com.sca4d.core.api.error.BusinessException;
import com.sca4d.core.api.id.IdGenerator;
import com.sca4d.iot.IotErrorCode;
import com.sca4d.iot.api.Device;
import com.sca4d.iot.api.ProductDefinition;
import com.sca4d.iot.api.change.DeviceChangeArea;
import com.sca4d.iot.api.message.DeviceDownlink;
import com.sca4d.iot.api.message.DeviceDownlinkKind;
import com.sca4d.iot.api.message.DeviceDownlinkStatus;
import com.sca4d.iot.api.model.ThingCommand;
import com.sca4d.iot.api.model.ThingModel;
import com.sca4d.iot.api.model.ThingModelPresentation;
import com.sca4d.iot.api.model.ThingModelRepository;
import com.sca4d.iot.change.DeviceChangeNotifier;
import com.sca4d.iot.device.DeviceRepository;
import com.sca4d.iot.model.ThingValueValidator;
import com.sca4d.iot.product.ProductDefinitionCatalog;
import org.jetbrains.annotations.CheckReturnValue;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/** 校验物模型 command 输入并创建一次性可靠下行消息。 */
public class DeviceCommandService {

    private static final Logger LOG = LoggerFactory.getLogger(DeviceCommandService.class);

    private final @NotNull DeviceRepository devices;

    private final @NotNull ProductDefinitionCatalog products;

    private final @NotNull DownlinkRepository downlinks;

    private final @NotNull ThingModelRepository models;

    private final @NotNull ThingValueValidator validator;

    private final @NotNull IdGenerator ids;

    private final @NotNull Clock clock;

    private final @NotNull DeviceChangeNotifier changes;

    private final @NotNull DeviceDeliveryScheduler messagePublisher;

    /** 创建 command 事务服务。 */
    public DeviceCommandService(
            @NotNull DeviceRepository devices,
            @NotNull ProductDefinitionCatalog products,
            @NotNull DownlinkRepository downlinks,
            @NotNull ThingModelRepository models,
            @NotNull ThingValueValidator validator,
            @NotNull IdGenerator ids,
            @NotNull Clock clock,
            @NotNull DeviceChangeNotifier changes,
            @NotNull DeviceDeliveryScheduler messagePublisher) {
        this.devices = Objects.requireNonNull(devices, "Device Repository 不能为空");
        this.products = Objects.requireNonNull(products, "ProductDefinition 目录不能为空");
        this.downlinks = Objects.requireNonNull(downlinks, "Downlink Repository 不能为空");
        this.models = Objects.requireNonNull(models, "ThingModelRepository 不能为空");
        this.validator = Objects.requireNonNull(validator, "Thing value 校验器不能为空");
        this.ids = Objects.requireNonNull(ids, "ID 生成器不能为空");
        this.clock = Objects.requireNonNull(clock, "command 时钟不能为空");
        this.changes = Objects.requireNonNull(changes, "Device 变化通知器不能为空");
        this.messagePublisher = Objects.requireNonNull(messagePublisher, "下行消息发布器不能为空");
    }

    /**
     * @param deviceId Device 标识
     * @param commandCode command code
     * @param inputs 完整输入
     * @param expiresAt 首次成功发布的最晚时间；普通即时 command 为空
     * @param acknowledgementTimeout 本条消息发送成功后等待 Device 业务 ACK 的时间
     * @return 新建下行消息
     */
    @Transactional
    @CheckReturnValue
    @SuppressWarnings("DuplicatedCode")
    public @NotNull DeviceDownlink submit(
            long deviceId,
            @NotNull String commandCode,
            @NotNull Map<String, ?> inputs,
            @Nullable Instant expiresAt,
            @NotNull Duration acknowledgementTimeout) {
        return submitCommand(
                deviceId,
                commandCode,
                inputs,
                expiresAt,
                acknowledgementTimeout,
                null,
                null);
    }

    /**
     * 使用代码物模型声明的类型化命令和完整输入创建可靠下行消息。
     *
     * <p>服务仍读取 Device 当前绑定的物模型并执行全部运行时约束，调用方持有的命令不能绕过当前模型版本。
     *
     * @param deviceId Device 标识
     * @param command 代码物模型声明并由调用方直接引用的命令
     * @param inputs 使用当前命令 `DataKey` 构造的完整输入
     * @param expiresAt 首次成功发布的最晚时间；普通即时命令为空
     * @param acknowledgementTimeout 本条消息发送成功后等待 Device 业务 ACK 的时间
     * @return 新建下行消息
     * @throws BusinessException 输入键不完整或目标 Device 当前物模型不匹配时抛出
     */
    @Transactional
    @CheckReturnValue
    public @NotNull DeviceDownlink submit(
            long deviceId,
            @NotNull ThingCommand command,
            @NotNull DataValues inputs,
            @Nullable Instant expiresAt,
            @NotNull Duration acknowledgementTimeout) {
        ThingCommand definition = Objects.requireNonNull(command, "ThingCommand 不能为空");
        DataValues values = Objects.requireNonNull(inputs, "命令输入不能为空");
        if (!Set.copyOf(definition.inputKeys().values()).equals(values.asMap().keySet())) {
            throw new BusinessException(IotErrorCode.INVALID_INPUT);
        }
        LinkedHashMap<String, Object> namedInputs = new LinkedHashMap<>();
        for (Map.Entry<String, DataKey<?>> entry : definition.inputKeys().entrySet()) {
            namedInputs.put(entry.getKey(), values.asMap().get(entry.getValue()));
        }
        return submitCommand(
                deviceId,
                definition.code(),
                namedInputs,
                expiresAt,
                acknowledgementTimeout,
                null,
                null);
    }

    /**
     * 创建由一个设备定时规则产生的 command 下行，并保存规则标识和原定触发时间。
     */
    @Transactional
    @CheckReturnValue
    public @NotNull DeviceDownlink submitScheduled(
            long deviceId,
            @NotNull String commandCode,
            @NotNull Map<String, ?> inputs,
            @NotNull Instant expiresAt,
            @NotNull Duration acknowledgementTimeout,
            long scheduleId,
            @NotNull Instant scheduledAt) {
        if (scheduleId <= 0) {
            throw new IllegalArgumentException("设备定时规则标识必须为正数");
        }
        return submitCommand(
                deviceId,
                commandCode,
                inputs,
                expiresAt,
                acknowledgementTimeout,
                scheduleId,
                Objects.requireNonNull(scheduledAt, "设备定时原计划时间不能为空"));
    }

    /**
     * 取消仍未结束的 command，并阻止之后的发送或重试。
     *
     * <p>与调用方的业务写入使用同一事务；已经进入协议发布的命令可能送达，具体应用负责补偿。
     *
     * @param messageId 标准 command 下行消息标识
     * @return 本次登记取消时为 true；消息已经终结时为 false
     * @throws BusinessException 消息不存在或不是 command 时抛出无效输入错误
     */
    @Transactional
    @CheckReturnValue
    public boolean cancel(long messageId) {
        DeviceDownlink message = downlinks.find(messageId)
                .orElseThrow(() -> new BusinessException(IotErrorCode.INVALID_INPUT));
        if (message.kind() != DeviceDownlinkKind.COMMAND) {
            throw new BusinessException(IotErrorCode.INVALID_INPUT);
        }
        if (message.status().terminal()) {
            return false;
        }
        // 条件更新与 ACK 竞争终态；取消事务提交后才对其他消费者可见。
        Instant now = clock.instant();
        boolean changed = downlinks.complete(messageId, DeviceDownlinkStatus.CANCELLED, null, now);
        if (changed) {
            changes.changed(message.deviceId(), DeviceChangeArea.DOWNLINK, now);
            LOG.debug("Device command 取消已写入事务 | deviceId={} messageId={}", message.deviceId(), messageId);
        }
        return changed;
    }

    /** 校验 command 并在当前事务内保存业务状态和可靠发送消息。 */
    private @NotNull DeviceDownlink submitCommand(
            long deviceId,
            @NotNull String commandCode,
            @NotNull Map<String, ?> inputs,
            @Nullable Instant expiresAt,
            @NotNull Duration acknowledgementTimeout,
            @Nullable Long scheduleId,
            @Nullable Instant scheduledAt) {
        Device device = devices.find(deviceId)
                .orElseThrow(() -> new BusinessException(IotErrorCode.DEVICE_NOT_FOUND));
        ProductDefinition product = products.definition(device.product());
        ThingModel model = models.find(product.code(), device.thingModelVersion())
                .map(ThingModelPresentation::model)
                .orElseThrow(() -> new BusinessException(IotErrorCode.THING_MODEL_NOT_FOUND));
        ThingCommand command = model.commands().get(commandCode);
        if (command == null || !command.inputs().keySet().equals(inputs.keySet())) {
            throw new BusinessException(IotErrorCode.INVALID_INPUT);
        }
        LinkedHashMap<String, Object> normalized = new LinkedHashMap<>();
        try {
            command.inputs().forEach((code, type) -> normalized.put(code, validator.validate(type, inputs.get(code))));
        } catch (IllegalArgumentException exception) {
            throw new BusinessException(IotErrorCode.INVALID_INPUT, exception);
        }
        Instant now = clock.instant();
        DeviceDownlink message = new DeviceDownlink(
                ids.nextId().value(),
                deviceId,
                device.bindingGeneration(),
                DeviceDownlinkKind.COMMAND,
                commandCode,
                null,
                scheduleId,
                scheduledAt,
                normalized,
                command.acknowledgementPolicy(),
                Objects.requireNonNull(acknowledgementTimeout, "ACK 等待时间不能为空"),
                command.retryPolicy(),
                DeviceDownlinkStatus.PENDING,
                0,
                expiresAt,
                now);
        downlinks.add(message);
        messagePublisher.publishDispatch(message.messageId(), 1, null);
        changes.changed(deviceId, DeviceChangeArea.DOWNLINK, now);
        LOG.info(
                "Device command 已提交 | deviceId={} messageId={} commandCode={}",
                deviceId,
                message.messageId(),
                commandCode);
        return message;
    }
}
