package com.sca4d.iot.api;

import com.sca4d.core.api.utils.TextUtils;
import org.jetbrains.annotations.CheckReturnValue;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/** 表示创建 Device 时对数据库 Product 标识或代码 Product code 的选择。 */
public final class ProductSelector {

    private final @NotNull ProductSource source;

    private final @Nullable Long databaseProductId;

    private final @Nullable String codeProductCode;

    private ProductSelector(
            @NotNull ProductSource source,
            @Nullable Long databaseProductId,
            @Nullable String codeProductCode) {
        this.source = source;
        this.databaseProductId = databaseProductId;
        this.codeProductCode = codeProductCode;
    }

    /** @return 选择指定数据库 Product 的入口 */
    @CheckReturnValue
    public static @NotNull ProductSelector database(long productId) {
        if (productId <= 0) {
            throw new IllegalArgumentException("数据库 Product 标识必须为正数");
        }
        return new ProductSelector(ProductSource.DATABASE, productId, null);
    }

    /** @return 选择指定代码 Product 的入口 */
    @CheckReturnValue
    public static @NotNull ProductSelector code(@NotNull String productCode) {
        return new ProductSelector(
                ProductSource.CODE,
                null,
                TextUtils.requireText(productCode, "代码 Product code"));
    }

    /** @return Product 来源 */
    public @NotNull ProductSource source() {
        return source;
    }

    /** @return 数据库 Product 标识；代码 Product 时为空 */
    public @Nullable Long databaseProductId() {
        return databaseProductId;
    }

    /** @return 代码 Product code；数据库 Product 时为空 */
    public @Nullable String codeProductCode() {
        return codeProductCode;
    }
}
