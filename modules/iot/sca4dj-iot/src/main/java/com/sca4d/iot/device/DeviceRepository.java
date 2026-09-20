package com.sca4d.iot.device;

import com.sca4d.core.api.persistence.AffectedRowCounts;
import com.sca4d.core.api.persistence.AffectedRows;
import com.sca4d.core.api.persistence.AffectedRowsMismatchException;
import com.sca4d.core.api.persistence.Patch;
import com.sca4d.core.api.persistence.ZeroRowPolicy;
import com.sca4d.core.api.query.Page;
import com.sca4d.core.spring.persistence.batch.SqlBatches;
import com.sca4d.iot.api.Device;
import com.sca4d.iot.api.DeviceAccessMethod;
import com.sca4d.iot.api.ProductBinding;
import com.sca4d.iot.api.ProductSource;
import com.sca4d.iot.api.connection.DeviceConnectionBinding;
import com.sca4d.iot.api.connection.DeviceConnectionProtocol;
import com.sca4d.iot.api.connection.DeviceConnectionResolver;
import com.sca4d.iot.persistence.DeviceAccessMethodState;
import com.sca4d.iot.persistence.DeviceDetailsUpdate;
import com.sca4d.iot.persistence.DeviceState;
import com.sca4d.iot.persistence.mapper.DeviceMapper;
import org.jetbrains.annotations.CheckReturnValue;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;

/** 把 Device 持久化状态和接入方式集合转换成完整 Device，并检查写入结果。 */
public class DeviceRepository implements DeviceConnectionResolver {

    private final @NotNull DeviceMapper mapper;

    private final @NotNull SqlBatches sqlBatches;

    /**
     * 创建设备持久化入口。
     *
     * @param mapper Device SQL Mapper
     * @param sqlBatches SQL 批处理器
     */
    public DeviceRepository(@NotNull DeviceMapper mapper, @NotNull SqlBatches sqlBatches) {
        this.mapper = Objects.requireNonNull(mapper, "Device Mapper 不能为空");
        this.sqlBatches = Objects.requireNonNull(sqlBatches, "SQL 批处理器不能为空");
    }

    /**
     * 保存 Device、可选直接连接绑定和接入方式。
     *
     * @param device 待保存的完整 Device
     * @throws AffectedRowsMismatchException 任一必要写入没有准确影响预期行数时抛出
     */
    public void add(@NotNull Device device) {
        Device checked = Objects.requireNonNull(device, "Device 不能为空");
        DeviceConnectionBinding connection = checked.connection().orElse(null);
        AffectedRowCounts.requireSingle(
                mapper.insertDevice(
                        checked.id(),
                        checked.product().source(),
                        checked.product().code(),
                        checked.product().databaseProductId().isPresent()
                                ? checked.product().databaseProductId().getAsLong()
                                : null,
                        checked.code(),
                        checked.serialNumber(),
                        checked.displayName(),
                        checked.remark(),
                        checked.thingModelVersion(),
                        checked.protocolCode(),
                        connection == null ? null : connection.definition().protocol().code(),
                        connection == null ? null : connection.definition().identifierType(),
                        connection == null ? null : connection.identifierValue()),
                ZeroRowPolicy.ERROR,
                "新增 Device");
        List<String> accessMethods = checked.accessMethods().stream().map(DeviceAccessMethod::code).toList();
        sqlBatches.forEach(
                accessMethods,
                2,
                0,
                batch -> AffectedRowCounts.requireExactly(
                        mapper.insertDeviceAccessMethods(checked.id(), batch),
                        batch.size(),
                        "新增 Device 接入方式"));
    }

    /**
     * 按标识查询完整 Device。
     *
     * @param id Device 标识
     * @return 匹配的 Device；不存在时为空
     */
    @CheckReturnValue
    public @NotNull Optional<Device> find(long id) {
        return find(DeviceQuery.forIds(List.of(id)).build()).getItems().stream().findFirst();
    }

    /**
     * 按 Device 领域条件查询并装载完整 Device。
     *
     * <p>有界标识查询按统一 SQL 限制分批执行，分页列表查询先统计完整命中数再读取当前页。
     *
     * @param query 有界标识或分页列表查询
     * @return 完整 Device 分页结果
     */
    @CheckReturnValue
    public @NotNull Page<Device> find(@NotNull DeviceQuery query) {
        DeviceQuery checked = Objects.requireNonNull(query, "Device 查询不能为空");
        List<Long> deviceIds = checked.deviceIds();
        if (deviceIds != null) {
            if (deviceIds.isEmpty()) {
                return Page.empty(checked.request().getPage());
            }
            List<DeviceState> rows = sqlBatches.query(
                    deviceIds,
                    1,
                    9,
                    batch -> selectRows(checked, batch, 0, batch.size()));
            Map<Long, DeviceState> byId = rows.stream()
                    .collect(Collectors.toMap(DeviceState::id, state -> state));
            List<DeviceState> ordered = deviceIds.stream().map(byId::get).filter(Objects::nonNull).toList();
            return new Page<>(hydrate(ordered), checked.request().getPage(), ordered.size());
        }
        long total = countRows(checked);
        List<DeviceState> rows = total == 0
                ? List.of()
                : selectRows(
                        checked,
                        null,
                        checked.request().getPage().offset(),
                        checked.request().getPage().size());
        return new Page<>(hydrate(rows), checked.request().getPage(), total);
    }

    /** @return Device 当前仍存在且 binding generation 未变化时为 {@code true} */
    @CheckReturnValue
    public boolean isCurrentBinding(long deviceId, long bindingGeneration) {
        return mapper.existsCurrentBinding(deviceId, bindingGeneration);
    }

    /**
     * 按连接协议、身份类型和公开值查询完整 Device。
     *
     * @param connection 完整直接连接绑定
     * @return 匹配的 Device；不存在时为空
     */
    @CheckReturnValue
    @Override
    public @NotNull Optional<Device> findDevice(@NotNull DeviceConnectionBinding connection) {
        DeviceConnectionBinding checked = Objects.requireNonNull(connection, "Device 连接绑定不能为空");
        return Optional.ofNullable(findDevices(List.of(checked)).get(checked));
    }

    /** 批量读取连接绑定匹配的 Device，并用一次关联集合查询完成装载。 */
    @CheckReturnValue
    @Override
    public @NotNull Map<DeviceConnectionBinding, Device> findDevices(
            @NotNull Collection<DeviceConnectionBinding> connections) {
        List<DeviceConnectionBinding> checked = List.copyOf(
                Objects.requireNonNull(connections, "Device 连接绑定不能为空"));
        LinkedHashMap<DeviceConnectionBinding, Device> result = new LinkedHashMap<>();
        List<DeviceState> rows = sqlBatches.query(
                checked,
                3,
                0,
                mapper::selectDevicesByConnectionIdentifiers);
        for (Device device : hydrate(rows)) {
            DeviceConnectionBinding binding = device.connection()
                    .orElseThrow(() -> new IllegalStateException("批量连接查询返回了间接 Device"));
            result.put(binding, device);
        }
        return Map.copyOf(result);
    }

    /**
     * 检查 Product 变更是否会移除任一已登记 Device 正在使用的接入方式或协议。
     *
     * @param productId 数据库 Product 标识
     * @param accessMethods 修改后允许的接入方式
     * @param protocolCodes 修改后兼容的协议编码
     * @return 存在不兼容 Device 时为 {@code true}
     */
    @CheckReturnValue
    public boolean existsIncompatibleProductUsage(
            long productId,
            @NotNull Collection<DeviceAccessMethod> accessMethods,
            @NotNull Collection<String> protocolCodes) {
        List<String> methods = Objects.requireNonNull(accessMethods, "Product 接入方式不能为空")
                .stream()
                .map(DeviceAccessMethod::code)
                .toList();
        List<String> protocols = List.copyOf(Objects.requireNonNull(protocolCodes, "Product 协议不能为空"));
        return mapper.existsDeviceIncompatibleWithProduct(productId, methods, protocols);
    }

    /**
     * 完整替换 Device 接入方式。
     *
     * @param deviceId Device 标识
     * @param accessMethods 已经按 Product 规则校验的新接入方式
     * @throws AffectedRowsMismatchException 接入方式写入没有准确影响预期行数时抛出
     */
    public void replaceAccessMethods(
            long deviceId,
            @NotNull Collection<DeviceAccessMethod> accessMethods) {
        Collection<DeviceAccessMethod> checked = List.copyOf(
                Objects.requireNonNull(accessMethods, "Device 接入方式不能为空"));
        mapper.deleteDeviceAccessMethods(deviceId);
        List<String> methodCodes = checked.stream().map(DeviceAccessMethod::code).toList();
        sqlBatches.forEach(
                methodCodes,
                2,
                0,
                batch -> AffectedRowCounts.requireExactly(
                        mapper.insertDeviceAccessMethods(deviceId, batch),
                        batch.size(),
                        "新增 Device 接入方式"));
    }

    /**
     * 修改 Device 展示名称和备注。
     *
     * @param deviceId Device 标识
     * @param displayName 新展示名称
     * @param remark 新备注；清除备注时为 {@code null}
     */
    @AffectedRows(target = "Device 展示资料")
    public void updateDetails(
            long deviceId,
            @NotNull String displayName,
            @Nullable String remark) {
        DeviceDetailsUpdate update = new DeviceDetailsUpdate(
                Patch.set(Objects.requireNonNull(displayName, "Device 展示名称不能为空")),
                Patch.resolve(remark, value -> value == null ? Patch.clear() : Patch.set(value)));
        updateDetails(deviceId, update);
    }

    /**
     * 执行已经解析完成的 Device 展示资料字段更新。
     *
     * @param deviceId Device 标识
     * @param update 展示名称和备注的字段更新意图
     * @throws IllegalArgumentException 两个字段都为 IGNORE 时抛出
     */
    void updateDetails(long deviceId, @NotNull DeviceDetailsUpdate update) {
        DeviceDetailsUpdate checked = Objects.requireNonNull(update, "Device 展示资料更新不能为空");
        if (!checked.hasUpdates()) {
            throw new IllegalArgumentException("Device 展示资料更新至少需要修改一个字段");
        }
        mapper.updateDeviceDetails(deviceId, checked);
    }

    /**
     * 按当前 binding generation 切换 Device 物模型和协议。
     *
     * @param deviceId Device 标识
     * @param expectedBindingGeneration 修改前绑定版本
     * @param thingModelVersion 新物模型版本
     * @param protocolCode 新协议编码
     */
    @AffectedRows(target = "Device 物模型绑定", whenZero = ZeroRowPolicy.OPTIMISTIC_LOCK)
    public void updateBinding(
            long deviceId,
            long expectedBindingGeneration,
            int thingModelVersion,
            @NotNull String protocolCode) {
        mapper.updateDeviceBinding(
                deviceId,
                expectedBindingGeneration,
                expectedBindingGeneration + 1,
                thingModelVersion,
                protocolCode);
    }

    /** 完整替换 Device 的可选直接连接绑定。 */
    @AffectedRows(target = "Device 连接绑定")
    public void updateConnection(long deviceId, @Nullable DeviceConnectionBinding connection) {
        mapper.updateDeviceConnection(
                deviceId,
                connection == null ? null : connection.definition().protocol().code(),
                connection == null ? null : connection.definition().identifierType(),
                connection == null ? null : connection.identifierValue());
    }

    /** 分批加载 Device 接入方式并组装完整领域对象。 */
    private @NotNull List<Device> hydrate(@NotNull List<DeviceState> rows) {
        List<DeviceState> checked = List.copyOf(rows);
        if (checked.isEmpty()) {
            return List.of();
        }

        // 一次读取当前批次的全部接入方式，数据库往返次数不随 Device 数量增加。
        List<Long> ids = checked.stream().map(DeviceState::id).toList();
        Map<Long, List<String>> accessMethods = new HashMap<>();
        for (DeviceAccessMethodState state : sqlBatches.query(
                ids,
                1,
                0,
                mapper::selectDeviceAccessMethodsBatch)) {
            accessMethods.computeIfAbsent(state.deviceId(), ignored -> new ArrayList<>())
                    .add(state.methodCode());
        }

        // 按标量查询的原顺序组装 Device，缺少接入方式时使用空集合。
        return checked.stream()
                .map(row -> toDevice(row, accessMethods.getOrDefault(row.id(), List.of())))
                .toList();
    }

    /** 把 DeviceQuery 的领域条件转换为 Mapper 的计数参数。 */
    private long countRows(@NotNull DeviceQuery query) {
        ProductBinding product = query.product();
        return mapper.countDevices(
                null,
                product == null ? null : product.source(),
                product == null ? null : product.code(),
                product == null || product.databaseProductId().isEmpty()
                        ? null
                        : product.databaseProductId().getAsLong(),
                query.search(),
                query.protocolCode(),
                query.directConnection());
    }

    /** 把 DeviceQuery 的领域条件、范围和分页转换为 Mapper 查询参数。 */
    private @NotNull List<DeviceState> selectRows(
            @NotNull DeviceQuery query,
            @Nullable Collection<Long> deviceIds,
            long offset,
            int size) {
        ProductBinding product = query.product();
        return mapper.selectDevices(
                deviceIds,
                product == null ? null : product.source(),
                product == null ? null : product.code(),
                product == null || product.databaseProductId().isEmpty()
                        ? null
                        : product.databaseProductId().getAsLong(),
                query.search(),
                query.protocolCode(),
                query.directConnection(),
                offset,
                size,
                query.request().getSort());
    }

    /** 将 Device 标量状态和已经读取的接入方式转换成完整领域对象。 */
    private Device toDevice(DeviceState row, Collection<String> accessMethodCodes) {
        return new Device(
                row.id(),
                row.productSource() == ProductSource.DATABASE
                        ? ProductBinding.database(
                                Objects.requireNonNull(row.productId(), "数据库 Product 标识不能为空"),
                                row.productCode())
                        : ProductBinding.code(row.productCode()),
                row.code(),
                row.serialNumber(),
                row.displayName(),
                row.remark(),
                row.thingModelVersion(),
                row.bindingGeneration(),
                row.protocolCode(),
                connection(row),
                accessMethodCodes.stream()
                        .map(DeviceAccessMethod::parse)
                        .toList());
    }

    /** 把三个可空连接列转换成完整绑定，并拒绝数据库中的部分空值。 */
    private static @Nullable DeviceConnectionBinding connection(DeviceState row) {
        String protocol = row.connectionProtocol();
        String identifierType = row.connectionIdentifierType();
        String identifierValue = row.connectionIdentifierValue();
        if (protocol == null && identifierType == null && identifierValue == null) {
            return null;
        }
        if (protocol == null || identifierType == null || identifierValue == null) {
            throw new IllegalStateException("Device 连接绑定字段不完整");
        }
        return new DeviceConnectionBinding(
                new DeviceConnectionProtocol(protocol),
                identifierType,
                identifierValue);
    }

}
