package com.sca4d.app.server.iot;

import org.jetbrains.annotations.NotNull;

import java.time.Instant;
import java.util.Objects;

/** 保存 Sca4d Server 为公共 IoT Product 增加的启停状态。 */
public class IotProductProfile {

    private final @NotNull Instant createdAt;

    private final @NotNull Instant updatedAt;

    private final @NotNull IotProfileStatus status;

    private final long productId;

    /** 创建数据库返回的 Product App Profile。 */
    public IotProductProfile(
            long productId,
            @NotNull IotProfileStatus status,
            @NotNull Instant createdAt,
            @NotNull Instant updatedAt) {
        this.productId = productId;
        this.status = Objects.requireNonNull(status, "Product App Profile 状态不能为空");
        this.createdAt = Objects.requireNonNull(createdAt, "Product App Profile 创建时间不能为空");
        this.updatedAt = Objects.requireNonNull(updatedAt, "Product App Profile 修改时间不能为空");
    }

    /** @return 公共 Product 标识 */
    public long productId() {
        return productId;
    }

    /** @return Sca4d Server 当前启停状态 */
    public @NotNull IotProfileStatus status() {
        return status;
    }

    /** @return App Profile 创建时间 */
    public @NotNull Instant createdAt() {
        return createdAt;
    }

    /** @return App Profile 最近修改时间 */
    public @NotNull Instant updatedAt() {
        return updatedAt;
    }
}
