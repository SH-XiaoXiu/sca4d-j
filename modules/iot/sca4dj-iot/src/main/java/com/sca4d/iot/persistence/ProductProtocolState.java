package com.sca4d.iot.persistence;

import org.jetbrains.annotations.NotNull;

import java.util.Objects;

/** 保存批量查询返回的 Product 标识和兼容协议编码。 */
public final class ProductProtocolState {

    private final long productId;

    private final @NotNull String protocolCode;

    /** @param productId Product 标识 @param protocolCode Product 兼容的协议编码 */
    public ProductProtocolState(long productId, @NotNull String protocolCode) {
        this.productId = productId;
        this.protocolCode = Objects.requireNonNull(protocolCode, "Product 协议编码不能为空");
    }

    /** @return Product 标识 */
    public long productId() {
        return productId;
    }

    /** @return Product 兼容的协议编码 */
    public @NotNull String protocolCode() {
        return protocolCode;
    }
}
