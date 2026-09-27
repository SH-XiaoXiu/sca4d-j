package com.sca4d.iot.persistence;

import org.jetbrains.annotations.NotNull;

import java.util.Objects;

/** 保存批量查询返回的 Product 标识和允许接入方式编码。 */
public class ProductAccessMethodState {

    private final long productId;

    private final @NotNull String methodCode;

    /** @param productId Product 标识 @param methodCode Product 允许的接入方式编码 */
    public ProductAccessMethodState(long productId, @NotNull String methodCode) {
        this.productId = productId;
        this.methodCode = Objects.requireNonNull(methodCode, "Product 接入方式编码不能为空");
    }

    /** @return Product 标识 */
    public long productId() {
        return productId;
    }

    /** @return Product 允许的接入方式编码 */
    public @NotNull String methodCode() {
        return methodCode;
    }
}
