package com.sca4d.iot.persistence.mapper;

import com.sca4d.core.api.name.code.TreeCode;
import com.sca4d.core.api.query.SortOrder;
import com.sca4d.iot.api.ProductSource;
import com.sca4d.iot.api.connection.DeviceConnectionBinding;
import com.sca4d.iot.persistence.DeviceAccessMethodState;
import com.sca4d.iot.persistence.DeviceDetailsUpdate;
import com.sca4d.iot.persistence.DeviceState;
import org.apache.ibatis.annotations.Param;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.List;

/** 读写主 PostgreSQL 中的 Device、连接身份、接入方式和物模型绑定。 */
public interface DeviceMapper {

    /**
     * 新增 Device 标量字段。
     *
     * @param id Device 标识
     * @param productSource Product 定义来源
     * @param productCode Product 稳定 code
     * @param productId 数据库 Product 标识；代码 Product 时为 {@code null}
     * @param code Product 内唯一编码
     * @param serialNumber 可选序列号
     * @param displayName 展示名称
     * @param remark 可选备注
     * @param thingModelVersion 绑定的物模型版本
     * @param protocolCode 绑定的协议编码
     * @param connectionProtocol 连接协议编码
     * @param connectionIdentifierType 连接身份类型
     * @param connectionIdentifierValue 连接身份值
     * @return 影响行数
     */
    int insertDevice(
            @Param("id") long id,
            @Param("productSource") @NotNull ProductSource productSource,
            @Param("productCode") @NotNull String productCode,
            @Param("productId") @Nullable Long productId,
            @Param("code") @NotNull String code,
            @Param("serialNumber") @Nullable String serialNumber,
            @Param("displayName") @NotNull String displayName,
            @Param("remark") @Nullable String remark,
            @Param("thingModelVersion") int thingModelVersion,
            @Param("protocolCode") @NotNull String protocolCode,
            @Param("connectionProtocol") @Nullable String connectionProtocol,
            @Param("connectionIdentifierType") @Nullable String connectionIdentifierType,
            @Param("connectionIdentifierValue") @Nullable String connectionIdentifierValue);

    /**
     * 新增 Device 接入方式。
     *
     * @param deviceId Device 标识
     * @param methodCodes 接入方式编码集合
     * @return 影响行数
     */
    int insertDeviceAccessMethods(
            @Param("deviceId") long deviceId,
            @Param("methodCodes") @NotNull Collection<@NotNull TreeCode> methodCodes);

    /** @return Device 当前仍存在且绑定版本未变化时为 {@code true} */
    boolean existsCurrentBinding(
            @Param("deviceId") long deviceId,
            @Param("bindingGeneration") long bindingGeneration);

    /** @param connections 连接协议、身份类型和值集合 @return 匹配的 Device 标量字段 */
    @NotNull
    List<DeviceState> selectDevicesByConnectionIdentifiers(
            @Param("connections") @NotNull Collection<DeviceConnectionBinding> connections);

    /** @return 符合 Device 标识、Product、文本、协议和连接方式条件的记录数量 */
    long countDevices(
            @Param("deviceIds") @Nullable Collection<Long> deviceIds,
            @Param("productSource") @Nullable ProductSource productSource,
            @Param("productCode") @Nullable String productCode,
            @Param("productId") @Nullable Long productId,
            @Param("search") @Nullable String search,
            @Param("protocolCode") @Nullable String protocolCode,
            @Param("directConnection") @Nullable Boolean directConnection);

    /** @return 按明确条件、分页和安全排序要求读取的 Device 标量字段 */
    @NotNull
    List<DeviceState> selectDevices(
            @Param("deviceIds") @Nullable Collection<Long> deviceIds,
            @Param("productSource") @Nullable ProductSource productSource,
            @Param("productCode") @Nullable String productCode,
            @Param("productId") @Nullable Long productId,
            @Param("search") @Nullable String search,
            @Param("protocolCode") @Nullable String protocolCode,
            @Param("directConnection") @Nullable Boolean directConnection,
            @Param("offset") long offset,
            @Param("size") int size,
            @Param("sort") @NotNull List<SortOrder> sort);

    /** @param deviceIds Device 标识集合 @return 按 Device 标识和编码排列的接入方式 */
    @NotNull
    List<DeviceAccessMethodState> selectDeviceAccessMethodsBatch(
            @Param("deviceIds") @NotNull Collection<Long> deviceIds);

    /**
     * 检查 Product 技术定义变更是否与任一已登记 Device 冲突。
     *
     * @param productId 数据库 Product 标识
     * @param accessMethodCodes 修改后允许的接入方式编码
     * @param protocolCodes 修改后兼容的协议编码
     * @return 存在使用已移除接入方式或协议的 Device 时为 {@code true}
     */
    boolean existsDeviceIncompatibleWithProduct(
            @Param("productId") long productId,
            @Param("accessMethodCodes") @NotNull Collection<TreeCode> accessMethodCodes,
            @Param("protocolCodes") @NotNull Collection<String> protocolCodes);

    /**
     * 删除 Device 当前全部接入方式。
     *
     * @param deviceId Device 标识
     * @return 影响行数
     */
    int deleteDeviceAccessMethods(@Param("deviceId") long deviceId);

    /**
     * 修改 Device 展示名称和备注。
     *
     * @param deviceId Device 标识
     * @param update 展示名称和备注的字段更新意图
     * @return 影响行数
     */
    int updateDeviceDetails(
            @Param("deviceId") long deviceId,
            @Param("update") @NotNull DeviceDetailsUpdate update);

    /**
     * 按预期绑定版本切换 Device 物模型和协议。
     *
     * @param deviceId Device 标识
     * @param expectedBindingGeneration 修改前绑定版本
     * @param bindingGeneration 修改后绑定版本
     * @param thingModelVersion 新物模型版本
     * @param protocolCode 新协议编码
     * @return 影响行数
     */
    int updateDeviceBinding(
            @Param("deviceId") long deviceId,
            @Param("expectedBindingGeneration") long expectedBindingGeneration,
            @Param("bindingGeneration") long bindingGeneration,
            @Param("thingModelVersion") int thingModelVersion,
            @Param("protocolCode") @NotNull String protocolCode);

    /**
     * 完整替换 Device 的可选直接连接绑定。
     *
     * @param deviceId Device 标识
     * @param connectionProtocol 连接协议；间接 Device 时为空
     * @param connectionIdentifierType 连接身份类型；间接 Device 时为空
     * @param connectionIdentifierValue 连接身份值；间接 Device 时为空
     * @return 影响行数
     */
    int updateDeviceConnection(
            @Param("deviceId") long deviceId,
            @Param("connectionProtocol") @Nullable String connectionProtocol,
            @Param("connectionIdentifierType") @Nullable String connectionIdentifierType,
            @Param("connectionIdentifierValue") @Nullable String connectionIdentifierValue);
}
