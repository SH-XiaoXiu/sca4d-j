package com.sca4d.iot.model;

import com.sca4d.core.api.i18n.LocalizedFields;
import com.sca4d.iot.api.ProductBinding;
import com.sca4d.iot.api.ProductDefinition;
import com.sca4d.iot.api.model.ThingModel;
import com.sca4d.iot.api.model.ThingModelPresentation;
import com.sca4d.iot.api.model.ThingModelRepository;
import com.sca4d.iot.persistence.DatabaseThingModelRepository;
import com.sca4d.iot.product.ProductDefinitionCatalog;
import org.jetbrains.annotations.CheckReturnValue;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

/** 按 Product code 把只读代码物模型和数据库物模型组合为唯一读写入口。 */
public final class CompositeThingModelRepository implements ThingModelRepository {

    private final @NotNull ProductDefinitionCatalog products;

    private final @NotNull StaticThingModelRepository codeModels;

    private final @NotNull DatabaseThingModelRepository databaseModels;

    /** 创建不允许代码 Product 运行时发布模型的组合 Repository。 */
    public CompositeThingModelRepository(
            @NotNull ProductDefinitionCatalog products,
            @NotNull StaticThingModelRepository codeModels,
            @NotNull DatabaseThingModelRepository databaseModels) {
        this.products = Objects.requireNonNull(products, "ProductDefinition 目录不能为空");
        this.codeModels = Objects.requireNonNull(codeModels, "代码 ThingModel Repository 不能为空");
        this.databaseModels = Objects.requireNonNull(databaseModels, "数据库 ThingModel Repository 不能为空");
    }

    @Override
    public @NotNull Optional<ThingModelPresentation> find(@NotNull String productCode, int version) {
        String code = Objects.requireNonNull(productCode, "Product code 不能为空");
        if (products.findCodeDefinition(code).isPresent()) {
            ProductDefinition product = products.definition(ProductBinding.code(code));
            return codeModels.find(product.code(), version);
        }
        return databaseModels.find(code, version);
    }

    @Override
    public @NotNull List<ThingModelPresentation> findPublished(@NotNull String productCode) {
        String code = Objects.requireNonNull(productCode, "Product code 不能为空");
        if (products.findCodeDefinition(code).isPresent()) {
            ProductDefinition product = products.definition(ProductBinding.code(code));
            return codeModels.findPublished(product.code());
        }
        return databaseModels.findPublished(code);
    }

    @Override
    @CheckReturnValue
    public @NotNull ThingModelPresentation publish(
            @NotNull ThingModel model,
            @NotNull LocalizedFields i18n) {
        ThingModel checked = Objects.requireNonNull(model, "ThingModel 不能为空");
        if (products.findCodeDefinition(checked.productCode()).isPresent()) {
            throw new UnsupportedOperationException("代码 Product 不支持运行时发布 ThingModel");
        }
        return databaseModels.publish(checked, Objects.requireNonNull(i18n, "物模型多语言字段不能为空"));
    }

    @Override
    @CheckReturnValue
    public @NotNull ThingModelPresentation replaceI18n(
            @NotNull String productCode,
            int version,
            @NotNull LocalizedFields i18n) {
        String code = Objects.requireNonNull(productCode, "Product code 不能为空");
        if (products.findCodeDefinition(code).isPresent()) {
            throw new UnsupportedOperationException("代码 Product 不支持运行时修改 ThingModel 翻译");
        }
        return databaseModels.replaceI18n(
                code,
                version,
                Objects.requireNonNull(i18n, "物模型多语言字段不能为空"));
    }
}
