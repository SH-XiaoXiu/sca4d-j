package com.sca4d.iot.api;

import com.sca4d.core.api.utils.TextUtils;
import org.jetbrains.annotations.CheckReturnValue;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Objects;
import java.util.OptionalLong;

/** 保存 Device 对已解析 Product 来源、稳定 code 和可选数据库标识的绑定。 */
public final class ProductBinding {

    private final @NotNull ProductSource source;

    private final @NotNull String code;

    private final @Nullable Long databaseProductId;

    /** 创建已经解析且满足来源约束的 Product 绑定。 */
    private ProductBinding(
            @NotNull ProductSource source,
            @NotNull String code,
            @Nullable Long databaseProductId) {
        this.source = Objects.requireNonNull(source, "Product 来源不能为空");
        this.code = TextUtils.requireText(code, "Product code");
        this.databaseProductId = databaseProductId;
        if ((source == ProductSource.DATABASE) != (databaseProductId != null)) {
            throw new IllegalArgumentException("数据库 Product 必须且只有数据库标识");
        }
        if (databaseProductId != null && databaseProductId <= 0) {
            throw new IllegalArgumentException("数据库 Product 标识必须为正数");
        }
    }

    /** @return 已解析的数据库 Product 绑定 */
    @CheckReturnValue
    public static @NotNull ProductBinding database(long productId, @NotNull String code) {
        return new ProductBinding(ProductSource.DATABASE, code, productId);
    }

    /** @return 已解析的代码 Product 绑定 */
    @CheckReturnValue
    public static @NotNull ProductBinding code(@NotNull String code) {
        return new ProductBinding(ProductSource.CODE, code, null);
    }

    /** @return Product 来源 */
    public @NotNull ProductSource source() {
        return source;
    }

    /** @return Product 稳定 code */
    public @NotNull String code() {
        return code;
    }

    /** @return 数据库 Product 标识；代码 Product 时为空 */
    public @NotNull OptionalLong databaseProductId() {
        return databaseProductId == null ? OptionalLong.empty() : OptionalLong.of(databaseProductId);
    }
}
