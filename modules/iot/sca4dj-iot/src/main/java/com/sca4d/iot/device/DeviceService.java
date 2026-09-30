package com.sca4d.iot.device;

import com.sca4d.core.api.concurrent.DistributedLock;
import com.sca4d.core.api.concurrent.LockKey;
import com.sca4d.core.api.error.BusinessException;
import com.sca4d.core.api.name.code.TreeCode;
import com.sca4d.core.api.query.Page;
import com.sca4d.iot.IotErrorCode;
import com.sca4d.iot.api.Device;
import com.sca4d.iot.api.ProductDefinition;
import com.sca4d.iot.api.change.DeviceChangeArea;
import com.sca4d.iot.api.connection.DeviceConnectionBinding;
import com.sca4d.iot.api.model.ThingModel;
import com.sca4d.iot.api.model.ThingModelPresentation;
import com.sca4d.iot.api.model.ThingModelRepository;
import com.sca4d.iot.api.protocol.DeviceProtocol;
import com.sca4d.iot.api.state.ReportedStateStore;
import com.sca4d.iot.change.DeviceChangeNotifier;
import com.sca4d.iot.message.DownlinkRepository;
import com.sca4d.iot.product.ProductDefinitionCatalog;
import com.sca4d.iot.product.ProductResolution;
import com.sca4d.iot.protocol.DeviceProtocolCatalog;
import org.jetbrains.annotations.CheckReturnValue;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Supplier;

/** 保存和修改 Device、Product、物模型、协议及接入方式等 IoT 技术状态。 */
public class DeviceService {

    private static final Logger LOG = LoggerFactory.getLogger(DeviceService.class);

    private static final Duration LOCK_WAIT = Duration.ofSeconds(10);

    private final @NotNull DeviceRepository devices;

    private final @NotNull DownlinkRepository downlinks;

    private final @NotNull ThingModelRepository models;

    private final @NotNull Clock clock;

    private final @NotNull DeviceChangeNotifier changes;

    private final @NotNull DeviceProtocolCatalog protocols;

    private final @NotNull DistributedLock distributedLock;

    private final @NotNull ProductDefinitionCatalog products;

    private final @NotNull ReportedStateStore reportedStates;

    private final @NotNull TransactionTemplate transactions;

    /** 创建设备台账服务。 */
    public DeviceService(
            @NotNull DeviceRepository devices,
            @NotNull ProductDefinitionCatalog products,
            @NotNull DownlinkRepository downlinks,
            @NotNull ReportedStateStore reportedStates,
            @NotNull ThingModelRepository models,
            @NotNull DeviceProtocolCatalog protocols,
            @NotNull Clock clock,
            @NotNull DeviceChangeNotifier changes,
            @NotNull DistributedLock distributedLock,
            @NotNull TransactionTemplate transactions) {
        this.devices = Objects.requireNonNull(devices, "Device Repository 不能为空");
        this.products = Objects.requireNonNull(products, "ProductDefinition 目录不能为空");
        this.downlinks = Objects.requireNonNull(downlinks, "Downlink Repository 不能为空");
        this.reportedStates = Objects.requireNonNull(reportedStates, "ReportedStateStore 不能为空");
        this.models = Objects.requireNonNull(models, "ThingModelRepository 不能为空");
        this.protocols = Objects.requireNonNull(protocols, "设备协议目录不能为空");
        this.clock = Objects.requireNonNull(clock, "Device 时钟不能为空");
        this.changes = Objects.requireNonNull(changes, "Device 变化通知器不能为空");
        this.distributedLock = Objects.requireNonNull(distributedLock, "分布式锁不能为空");
        this.transactions = Objects.requireNonNull(transactions, "事务模板不能为空");
    }

    /**
     * 使用调用方已经分配的稳定标识创建 Device。
     *
     * <p>当前方法不创建 Subject、Account、Credential 或 Session。具体应用应在同一业务事务中先分配
     * Device 主体标识，再调用本方法保存 IoT 台账。
     *
     * @param deviceId 具体应用分配的 Device 标识
     * @param registration Device 的 Product、协议、物模型、连接身份和接入方式
     * @return 新建 Device
     * @throws BusinessException Product、协议、物模型或连接身份不合法，或者唯一身份冲突时抛出
     */
    @CheckReturnValue
    public @NotNull Device create(long deviceId, @NotNull DeviceRegistration registration) {
        DeviceRegistration input = Objects.requireNonNull(registration, "Device 创建数据不能为空");
        ProductResolution product = products.resolve(input.product());
        return writeProductDevice(
                product.binding().code(),
                deviceId,
                () -> createLocked(deviceId, input));
    }

    /** 在 Product 与 Device 聚合锁内重新解析 Product 并保存 Device。 */
    private @NotNull Device createLocked(long deviceId, @NotNull DeviceRegistration input) {
        ProductResolution product = products.resolve(input.product());
        validateRegistration(product.definition(), input);
        Device device;
        try {
            device = new Device(
                    deviceId,
                    product.binding(),
                    input.code(),
                    input.serialNumber(),
                    input.displayName(),
                    input.remark(),
                    input.thingModelVersion(),
                    1,
                    input.protocolCode(),
                    input.connection().orElse(null),
                    input.accessMethods());
        } catch (IllegalArgumentException exception) {
            throw new BusinessException(IotErrorCode.INVALID_INPUT, exception);
        }
        try {
            devices.add(device);
        } catch (DuplicateKeyException exception) {
            throw new BusinessException(IotErrorCode.IDENTITY_CONFLICT, exception);
        }
        changes.changed(device.id(), DeviceChangeArea.DEVICE, clock.instant());
        LOG.info(
                "Device 已创建 | deviceId={} productCode={} productSource={} methodCount={}",
                device.id(),
                device.product().code(),
                device.product().source(),
                device.accessMethods().size());
        return device;
    }

    /** @param deviceId Device 标识 @return 必须存在的 Device */
    @Transactional(readOnly = true)
    @CheckReturnValue
    public @NotNull Device loadById(long deviceId) {
        return find(DeviceQuery.forIds(List.of(deviceId)).build())
                .getItems()
                .stream()
                .findFirst()
                .orElseThrow(() -> new BusinessException(IotErrorCode.DEVICE_NOT_FOUND));
    }

    /**
     * 按有界标识或分页列表条件查询 Device。
     *
     * @param query Device 台账支持的 Product、文本、协议和连接方式条件
     * @return 完整 Device 分页结果；有界标识查询的总数为实际匹配数量
     */
    @Transactional(readOnly = true)
    @CheckReturnValue
    public @NotNull Page<Device> find(@NotNull DeviceQuery query) {
        Page<Device> result = devices.find(Objects.requireNonNull(query, "Device 查询不能为空"));
        LOG.debug("Device 查询完成 | matchedCount={} returnedCount={}", result.getTotal(), result.getItems().size());
        return result;
    }

    /** @param connection 完整直接连接绑定 @return 匹配的 Device；不存在时为空 */
    @Transactional(readOnly = true)
    @CheckReturnValue
    public @NotNull Optional<Device> findByConnection(@NotNull DeviceConnectionBinding connection) {
        return devices.findDevice(Objects.requireNonNull(connection, "Device 连接绑定不能为空"));
    }

    /**
     * 校验 Device 当前 Product、物模型、协议、连接身份和接入方式已经具备启用条件。
     *
     * <p>当前方法只检查 IoT 模块负责的技术状态，不读取 Account 或 Credential。具体应用应另外检查
     * 自己安装的认证方法是否已经准备完成。
     *
     * @param deviceId 待启用 Device 标识
     * @throws BusinessException Product、物模型、协议、连接身份或接入方式不完整时抛出
     */
    @Transactional(readOnly = true)
    public void validateTechnicalActivation(long deviceId) {
        Device device = loadById(deviceId);
        ProductDefinition product = products.definition(device.product());
        if (!product.accessMethods().containsAll(device.accessMethods())
                || !product.protocolCodes().contains(device.protocolCode())) {
            throw new BusinessException(IotErrorCode.STATE_CONFLICT);
        }
        ThingModel model = models.find(product.code(), device.thingModelVersion())
                .map(ThingModelPresentation::model)
                .orElseThrow(() -> new BusinessException(IotErrorCode.THING_MODEL_NOT_FOUND));
        try {
            DeviceProtocol protocol = protocols.loadByCode(device.protocolCode());
            protocol.validate(model);
        } catch (IllegalArgumentException exception) {
            throw new BusinessException(IotErrorCode.PROTOCOL_CONFLICT, exception);
        }
    }

    /** 修改 Device 的展示名称和备注。 */
    public void updateDetails(long deviceId, @NotNull DeviceDetailsChange change) {
        DeviceDetailsChange input = Objects.requireNonNull(change, "Device 展示资料不能为空");
        writeDevice(deviceId, () -> updateDetailsLocked(deviceId, input));
    }

    /** 在 Device 聚合锁和事务内修改展示资料。 */
    private void updateDetailsLocked(long deviceId, @NotNull DeviceDetailsChange input) {
        Device current = devices.find(deviceId)
                .orElseThrow(() -> new BusinessException(IotErrorCode.DEVICE_NOT_FOUND));
        Device updated = new Device(
                current.id(),
                current.product(),
                current.code(),
                current.serialNumber(),
                input.displayName(),
                input.remark(),
                current.thingModelVersion(),
                current.bindingGeneration(),
                current.protocolCode(),
                current.connection().orElse(null),
                current.accessMethods());
        devices.updateDetails(deviceId, updated.displayName(), updated.remark());
        changes.changed(deviceId, DeviceChangeArea.DEVICE, clock.instant());
    }

    /**
     * 在具体应用确认 Device 已停用后切换物模型版本和设备协议。
     *
     * <p>当前绑定存在待发送、等待 ACK 或等待重试的下行时拒绝；成功后 binding generation 递增，
     * 新绑定的 reported 和 desired 从空状态开始；旧绑定的当前投影在同一次业务动作中删除。
     *
     * @param deviceId Device 标识
     * @param change 目标已发布物模型版本和兼容协议
     */
    public void rebindDisabledDevice(long deviceId, @NotNull DeviceBindingChange change) {
        Device existing = devices.find(deviceId)
                .orElseThrow(() -> new BusinessException(IotErrorCode.DEVICE_NOT_FOUND));
        DeviceBindingChange input = Objects.requireNonNull(change, "Device 绑定修改不能为空");
        writeProductDevice(
                existing.product().code(),
                deviceId,
                () -> rebindDisabledDeviceLocked(deviceId, input));
    }

    /** 在 Product 与 Device 聚合锁内切换物模型绑定。 */
    private void rebindDisabledDeviceLocked(long deviceId, @NotNull DeviceBindingChange input) {
        Device current = devices.find(deviceId)
                .orElseThrow(() -> new BusinessException(IotErrorCode.DEVICE_NOT_FOUND));
        if (current.thingModelVersion() == input.thingModelVersion()
                && current.protocolCode().equals(input.protocolCode())) {
            return;
        }
        ProductDefinition product = products.definition(current.product());
        if (!product.protocolCodes().contains(input.protocolCode())
                || downlinks.hasActiveMessages(deviceId, current.bindingGeneration())) {
            throw new BusinessException(IotErrorCode.STATE_CONFLICT);
        }
        ThingModel model = models.find(product.code(), input.thingModelVersion())
                .map(ThingModelPresentation::model)
                .orElseThrow(() -> new BusinessException(IotErrorCode.THING_MODEL_NOT_FOUND));
        try {
            protocols.loadByCode(input.protocolCode()).validate(model);
        } catch (IllegalArgumentException exception) {
            throw new BusinessException(IotErrorCode.PROTOCOL_CONFLICT, exception);
        }
        Instant now = clock.instant();
        devices.updateBinding(
                deviceId,
                current.bindingGeneration(),
                input.thingModelVersion(),
                input.protocolCode());
        reportedStates.delete(deviceId, current.bindingGeneration());
        int desiredCount = downlinks.deleteDesiredState(deviceId, current.bindingGeneration());
        Device updated = devices.find(deviceId)
                .orElseThrow(() -> new IllegalStateException("切换绑定后 Device 不存在"));
        changes.changed(deviceId, DeviceChangeArea.DEVICE, now);
        LOG.info(
                "Device 物模型绑定已切换 | deviceId={} modelVersion={} protocol={} bindingGeneration={} removedDesired={}",
                deviceId,
                updated.thingModelVersion(),
                updated.protocolCode(),
                updated.bindingGeneration(),
                desiredCount);
    }

    /**
     * 完整替换已经由具体应用确认停用的 Device 直接连接绑定和接入方式。
     *
     * @param deviceId Device 标识
     * @param change 可选直接连接绑定和 Product 允许的接入方式子集
     * @return 修改后的 Device；输入与当前值相同时返回当前 Device
     */
    @CheckReturnValue
    public @NotNull Device changeConnection(
            long deviceId,
            @NotNull DeviceConnectionChange change) {
        Device existing = devices.find(deviceId)
                .orElseThrow(() -> new BusinessException(IotErrorCode.DEVICE_NOT_FOUND));
        DeviceConnectionChange input = Objects.requireNonNull(change, "Device 连接修改不能为空");
        return writeProductDevice(
                existing.product().code(),
                deviceId,
                () -> changeConnectionLocked(deviceId, input));
    }

    /** 在 Product 与 Device 聚合锁内修改连接绑定。 */
    private @NotNull Device changeConnectionLocked(
            long deviceId,
            @NotNull DeviceConnectionChange input) {
        Device current = devices.find(deviceId)
                .orElseThrow(() -> new BusinessException(IotErrorCode.DEVICE_NOT_FOUND));
        ProductDefinition product = products.definition(current.product());
        Device target;
        try {
            target = new Device(
                    current.id(),
                    current.product(),
                    current.code(),
                    current.serialNumber(),
                    current.displayName(),
                    current.remark(),
                    current.thingModelVersion(),
                    current.bindingGeneration(),
                    current.protocolCode(),
                    input.connection().orElse(null),
                    input.accessMethods());
        } catch (IllegalArgumentException exception) {
            throw new BusinessException(IotErrorCode.INVALID_INPUT, exception);
        }
        if (!product.accessMethods().containsAll(target.accessMethods())) {
            throw new BusinessException(IotErrorCode.INVALID_INPUT);
        }
        if (current.connection().equals(target.connection())
                && current.accessMethods().equals(target.accessMethods())) {
            return current;
        }
        devices.updateConnection(deviceId, target.connection().orElse(null));
        devices.replaceAccessMethods(deviceId, target.accessMethods());
        Device updated = devices.find(deviceId)
                .orElseThrow(() -> new IllegalStateException("修改接入方式后 Device 不存在"));
        changes.changed(deviceId, DeviceChangeArea.DEVICE, clock.instant());
        LOG.info(
                "Device 连接绑定已修改 | deviceId={} direct={} methodCount={}",
                deviceId,
                updated.connection().isPresent(),
                updated.accessMethods().size());
        return updated;
    }

    /** 取得 Device 聚合锁后，在事务内执行无返回值的写操作。 */
    private void writeDevice(long deviceId, @NotNull Runnable operation) {
        distributedLock.execute(
                new LockKey(TreeCode.of("IOT:DEVICE"), deviceId),
                LOCK_WAIT,
                () -> {
                    transactions.executeWithoutResult(status -> operation.run());
                    return null;
                });
    }

    /** 按 Product、Device 的固定顺序取得聚合锁，再在事务内执行有返回值的写操作。 */
    private <T> @NotNull T writeProductDevice(
            @NotNull String productCode,
            long deviceId,
            @NotNull Supplier<@NotNull T> operation) {
        return distributedLock.execute(
                new LockKey(TreeCode.of("IOT:PRODUCT"), productCode),
                LOCK_WAIT,
                () -> distributedLock.execute(
                        new LockKey(TreeCode.of("IOT:DEVICE"), deviceId),
                        LOCK_WAIT,
                        () -> Objects.requireNonNull(
                                transactions.execute(status -> operation.get()),
                                "Device 写操作结果不能为空")));
    }

    /** 按 Product、Device 的固定顺序取得聚合锁，再在事务内执行无返回值的写操作。 */
    private void writeProductDevice(
            @NotNull String productCode,
            long deviceId,
            @NotNull Runnable operation) {
        distributedLock.execute(
                new LockKey(TreeCode.of("IOT:PRODUCT"), productCode),
                LOCK_WAIT,
                () -> distributedLock.execute(
                        new LockKey(TreeCode.of("IOT:DEVICE"), deviceId),
                        LOCK_WAIT,
                        () -> {
                            transactions.executeWithoutResult(status -> operation.run());
                            return null;
                        }));
    }

    /** 校验 Device 是 Product 允许的确定子集并且物模型和协议存在。 */
    private void validateRegistration(ProductDefinition product, DeviceRegistration input) {
        if (input.connection().isPresent() == input.accessMethods().isEmpty()
                || !product.accessMethods().containsAll(input.accessMethods())) {
            throw new BusinessException(IotErrorCode.INVALID_INPUT);
        }
        if (!product.protocolCodes().contains(input.protocolCode())) {
            throw new BusinessException(IotErrorCode.PROTOCOL_CONFLICT);
        }
        ThingModel model = models.find(product.code(), input.thingModelVersion())
                .map(ThingModelPresentation::model)
                .orElseThrow(() -> new BusinessException(IotErrorCode.THING_MODEL_NOT_FOUND));
        DeviceProtocol protocol = protocols.loadByCode(input.protocolCode());
        protocol.validate(model);
    }
}
