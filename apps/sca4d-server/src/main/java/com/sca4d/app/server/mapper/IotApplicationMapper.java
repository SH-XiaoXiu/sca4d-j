package com.sca4d.app.server.mapper;

import com.sca4d.app.server.iot.DeviceCreationRequestRecord;
import com.sca4d.app.server.iot.IotDeviceProfile;
import com.sca4d.app.server.iot.IotProductProfile;
import com.sca4d.app.server.iot.IotProfileStatus;
import org.apache.ibatis.annotations.Param;
import org.jetbrains.annotations.CheckReturnValue;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.time.Instant;
import java.util.Collection;
import java.util.List;

/** 定义 Sca4d Server 的 IoT 启停状态和 HTTP 幂等记录使用的最小 SQL。 */
public interface IotApplicationMapper {

    /** 新增默认停用的 Product App Profile。 */
    int insertProductProfile(
            @Param("productId") long productId,
            @Param("status") @NotNull IotProfileStatus status);

    /** 按 Product 标识读取 App Profile。 */
    @CheckReturnValue
    @Nullable
    IotProductProfile selectProductProfile(@Param("productId") long productId);

    /** @param productIds Product 标识集合 @return 对应的 App Profile */
    @NotNull
    List<IotProductProfile> selectProductProfiles(
            @Param("productIds") @NotNull Collection<Long> productIds);

    /** 覆盖 Product App Profile 状态。 */
    int updateProductStatus(
            @Param("productId") long productId,
            @Param("status") @NotNull IotProfileStatus status);

    /** 新增默认停用的 Device App Profile。 */
    int insertDeviceProfile(
            @Param("deviceId") long deviceId,
            @Param("status") @NotNull IotProfileStatus status);

    /** 按 Device 标识读取 App Profile。 */
    @CheckReturnValue
    @Nullable
    IotDeviceProfile selectDeviceProfile(@Param("deviceId") long deviceId);

    /** @param deviceIds Device 标识集合 @return 对应的 App Profile */
    @NotNull
    List<IotDeviceProfile> selectDeviceProfiles(
            @Param("deviceIds") @NotNull Collection<Long> deviceIds);

    /** 覆盖 Device App Profile 状态。 */
    int updateDeviceStatus(
            @Param("deviceId") long deviceId,
            @Param("status") @NotNull IotProfileStatus status);

    /** 删除已经超过重放窗口的同名幂等记录。 */
    @CheckReturnValue
    int deleteExpiredDeviceCreation(
            @Param("idempotencyKey") @NotNull String idempotencyKey,
            @Param("now") @NotNull Instant now);

    /** 分批删除全部已经超过重放窗口的幂等记录。 */
    @CheckReturnValue
    int deleteExpiredDeviceCreations(
            @Param("now") @NotNull Instant now,
            @Param("batchSize") int batchSize);

    /** 尝试认领新的 Device 创建幂等键。 */
    @CheckReturnValue
    int insertPendingDeviceCreation(
            @Param("idempotencyKey") @NotNull String idempotencyKey,
            @Param("requestHash") @NotNull String requestHash,
            @Param("expiresAt") @NotNull Instant expiresAt);

    /** 把当前事务认领的幂等记录改为可重放结果。 */
    int completeDeviceCreation(
            @Param("idempotencyKey") @NotNull String idempotencyKey,
            @Param("requestHash") @NotNull String requestHash,
            @Param("responseKeyId") @NotNull String responseKeyId,
            @Param("responseCiphertext") byte @NotNull [] responseCiphertext);

    /** 读取仍在重放窗口内且已经完成的 Device 创建结果。 */
    @CheckReturnValue
    @Nullable
    DeviceCreationRequestRecord selectCompletedDeviceCreation(
            @Param("idempotencyKey") @NotNull String idempotencyKey,
            @Param("now") @NotNull Instant now);
}
