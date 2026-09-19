package com.sca4d.app.server.iot;

import com.sca4d.iot.api.ProductBinding;
import com.sca4d.iot.api.ProductDefinition;
import com.sca4d.iot.api.ProductSource;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Objects;

/** 组合任意来源 Product 技术定义和可选数据库 App Profile。 */
public final class IotProductCatalogItem {

    private final @NotNull ProductBinding binding;

    private final @NotNull ProductDefinition definition;

    private final @Nullable IotProductProfile profile;

    /** 创建来源、定义和 Profile 一致的 Product 管理目录项。 */
    public IotProductCatalogItem(
            @NotNull ProductBinding binding,
            @NotNull ProductDefinition definition,
            @Nullable IotProductProfile profile) {
        this.binding = Objects.requireNonNull(binding, "Product 绑定不能为空");
        this.definition = Objects.requireNonNull(definition, "Product 定义不能为空");
        this.profile = profile;
        if (!binding.code().equals(definition.code())) {
            throw new IllegalArgumentException("Product 绑定与定义 code 不一致");
        }
        if (binding.source() == ProductSource.DATABASE) {
            long productId = binding.databaseProductId()
                    .orElseThrow(() -> new IllegalArgumentException("数据库 Product 缺少标识"));
            if (profile == null || profile.productId() != productId) {
                throw new IllegalArgumentException("数据库 Product 与 App Profile 标识不一致");
            }
        } else if (profile != null) {
            throw new IllegalArgumentException("代码 Product 不能包含数据库 App Profile");
        }
    }

    /** @return Product 来源、code 和可选数据库标识 */
    public @NotNull ProductBinding binding() {
        return binding;
    }

    /** @return Product 只读技术定义 */
    public @NotNull ProductDefinition definition() {
        return definition;
    }

    /** @return 数据库 Product App Profile；代码 Product 时为空 */
    public @Nullable IotProductProfile profile() {
        return profile;
    }
}
