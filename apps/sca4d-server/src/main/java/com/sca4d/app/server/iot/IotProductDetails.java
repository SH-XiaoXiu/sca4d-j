package com.sca4d.app.server.iot;

import com.sca4d.iot.api.Product;
import org.jetbrains.annotations.NotNull;

import java.util.Objects;

/** 把公共 Product 与 Sca4d Server 的启停 Profile 组合成一个应用查询结果。 */
public class IotProductDetails {

    private final @NotNull IotProductProfile profile;

    private final @NotNull Product product;

    /** 创建标识一致的 Product 应用查询结果。 */
    public IotProductDetails(@NotNull Product product, @NotNull IotProductProfile profile) {
        this.product = Objects.requireNonNull(product, "Product 不能为空");
        this.profile = Objects.requireNonNull(profile, "Product App Profile 不能为空");
        if (product.id() != profile.productId()) {
            throw new IllegalArgumentException("Product 与 App Profile 标识不一致");
        }
    }

    /** @return 公共 Product */
    public @NotNull Product product() {
        return product;
    }

    /** @return Sca4d Server Product Profile */
    public @NotNull IotProductProfile profile() {
        return profile;
    }
}
