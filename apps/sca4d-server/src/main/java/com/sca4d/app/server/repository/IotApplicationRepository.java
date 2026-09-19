package com.sca4d.app.server.repository;

import com.sca4d.app.server.iot.DeviceCreationRequestRecord;
import com.sca4d.app.server.iot.IotDeviceProfile;
import com.sca4d.app.server.iot.IotProductProfile;
import com.sca4d.app.server.iot.IotProfileStatus;
import com.sca4d.app.server.mapper.IotApplicationMapper;
import com.sca4d.core.api.persistence.AffectedRowCounts;
import com.sca4d.core.api.persistence.AffectedRows;
import com.sca4d.core.api.persistence.ZeroRowPolicy;
import org.jetbrains.annotations.CheckReturnValue;
import org.jetbrains.annotations.NotNull;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

/** 隐藏 Sca4d Server IoT App Profile 与创建幂等表的 Mapper 和影响行数。 */
@Repository
public class IotApplicationRepository {

    private final @NotNull IotApplicationMapper mapper;

    /** 创建 Sca4d Server 的 IoT 应用持久化入口。 */
    IotApplicationRepository(@NotNull IotApplicationMapper mapper) {
        this.mapper = Objects.requireNonNull(mapper, "IoT 应用 Mapper 不能为空");
    }

    /** 新增默认停用的 Product App Profile。 */
    @AffectedRows(target = "新增 Product App Profile")
    public void addProductProfile(long productId) {
        mapper.insertProductProfile(productId, IotProfileStatus.DISABLED);
    }

    /** @return Product App Profile；不存在时为空 */
    @CheckReturnValue
    public @NotNull Optional<IotProductProfile> findProductProfile(long productId) {
        return Optional.ofNullable(mapper.selectProductProfile(productId));
    }

    /** @param productIds Product 标识集合 @return 按 Product 标识索引的 App Profile */
    @CheckReturnValue
    public @NotNull Map<Long, IotProductProfile> findProductProfiles(
            @NotNull Collection<Long> productIds) {
        List<Long> ids = List.copyOf(Objects.requireNonNull(productIds, "Product 标识不能为空"));
        if (ids.isEmpty()) {
            return Map.of();
        }
        return mapper.selectProductProfiles(ids)
                .stream()
                .collect(Collectors.toUnmodifiableMap(IotProductProfile::productId, Function.identity()));
    }

    /** 覆盖 Product App Profile 状态。 */
    @AffectedRows(target = "Product App Profile")
    public void changeProductStatus(
            long productId,
            @NotNull IotProfileStatus status) {
        mapper.updateProductStatus(productId, status);
    }

    /** 新增默认停用的 Device App Profile。 */
    @AffectedRows(target = "新增 Device App Profile")
    public void addDeviceProfile(long deviceId) {
        mapper.insertDeviceProfile(deviceId, IotProfileStatus.DISABLED);
    }

    /** @return Device App Profile；不存在时为空 */
    @CheckReturnValue
    public @NotNull Optional<IotDeviceProfile> findDeviceProfile(long deviceId) {
        return Optional.ofNullable(mapper.selectDeviceProfile(deviceId));
    }

    /** @param deviceIds Device 标识集合 @return 按 Device 标识索引的 App Profile */
    @CheckReturnValue
    public @NotNull Map<Long, IotDeviceProfile> findDeviceProfiles(
            @NotNull Collection<Long> deviceIds) {
        List<Long> ids = List.copyOf(Objects.requireNonNull(deviceIds, "Device 标识不能为空"));
        if (ids.isEmpty()) {
            return Map.of();
        }
        return mapper.selectDeviceProfiles(ids)
                .stream()
                .collect(Collectors.toUnmodifiableMap(IotDeviceProfile::deviceId, Function.identity()));
    }

    /** 覆盖 Device App Profile 状态。 */
    @AffectedRows(target = "Device App Profile")
    public void changeDeviceStatus(
            long deviceId,
            @NotNull IotProfileStatus status) {
        mapper.updateDeviceStatus(deviceId, status);
    }

    /** 删除过期同名记录后尝试认领幂等键。 */
    @CheckReturnValue
    public boolean claimDeviceCreation(
            @NotNull String idempotencyKey,
            @NotNull String requestHash,
            @NotNull Instant expiresAt,
            @NotNull Instant now) {
        AffectedRowCounts.requireSingle(
                mapper.deleteExpiredDeviceCreation(idempotencyKey, now),
                ZeroRowPolicy.ALLOW,
                "删除过期 Device 创建幂等记录");
        return AffectedRowCounts.changed(
                mapper.insertPendingDeviceCreation(idempotencyKey, requestHash, expiresAt),
                "认领 Device 创建幂等键");
    }

    /** 分批删除已经超过十分钟重放窗口的 Device 创建幂等记录。 */
    public int deleteExpiredDeviceCreations(@NotNull Instant now, int batchSize) {
        if (batchSize < 1 || batchSize > 10_000) {
            throw new IllegalArgumentException("Device 创建幂等清理批量必须在 1 到 10000 之间");
        }
        return mapper.deleteExpiredDeviceCreations(
                Objects.requireNonNull(now, "Device 创建幂等清理时间不能为空"),
                batchSize);
    }

    /** 保存当前事务新建 Device 的加密重放结果。 */
    @AffectedRows(target = "Device 创建幂等结果", whenZero = ZeroRowPolicy.OPTIMISTIC_LOCK)
    public void completeDeviceCreation(
            @NotNull String idempotencyKey,
            @NotNull String requestHash,
            @NotNull String responseKeyId,
            byte @NotNull [] responseCiphertext) {
        mapper.completeDeviceCreation(
                idempotencyKey,
                requestHash,
                responseKeyId,
                responseCiphertext);
    }

    /** @return 当前仍可重放的完成结果；不存在或未完成时为空 */
    @CheckReturnValue
    public @NotNull Optional<DeviceCreationRequestRecord> findCompletedDeviceCreation(
            @NotNull String idempotencyKey,
            @NotNull Instant now) {
        return Optional.ofNullable(mapper.selectCompletedDeviceCreation(idempotencyKey, now));
    }
}
