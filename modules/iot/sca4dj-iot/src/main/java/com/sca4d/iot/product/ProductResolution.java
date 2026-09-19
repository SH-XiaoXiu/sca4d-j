package com.sca4d.iot.product;

import com.sca4d.iot.api.ProductBinding;
import com.sca4d.iot.api.ProductDefinition;
import org.jetbrains.annotations.NotNull;

import java.util.Objects;

/** 保存一次 Product 选择解析得到的稳定 Device 绑定和技术定义。 */
public final class ProductResolution {

    private final @NotNull ProductBinding binding;

    private final @NotNull ProductDefinition definition;

    /** 创建来源一致的 Product 解析结果。 */
    ProductResolution(@NotNull ProductBinding binding, @NotNull ProductDefinition definition) {
        this.binding = Objects.requireNonNull(binding, "Product 绑定不能为空");
        this.definition = Objects.requireNonNull(definition, "Product 定义不能为空");
        if (!binding.code().equals(definition.code())) {
            throw new IllegalArgumentException("Product 绑定与定义 code 不一致");
        }
    }

    /** @return Device 应保存的 Product 绑定 */
    public @NotNull ProductBinding binding() {
        return binding;
    }

    /** @return Device 技术校验使用的 Product 定义 */
    public @NotNull ProductDefinition definition() {
        return definition;
    }
}
