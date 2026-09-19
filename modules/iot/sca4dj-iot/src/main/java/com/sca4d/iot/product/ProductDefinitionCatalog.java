package com.sca4d.iot.product;

import com.sca4d.core.api.error.BusinessException;
import com.sca4d.core.api.query.Page;
import com.sca4d.core.api.query.PageRequest;
import com.sca4d.iot.IotErrorCode;
import com.sca4d.iot.api.Product;
import com.sca4d.iot.api.ProductBinding;
import com.sca4d.iot.api.ProductDefinition;
import com.sca4d.iot.api.ProductSelector;
import com.sca4d.iot.api.ProductSource;
import org.jetbrains.annotations.CheckReturnValue;
import org.jetbrains.annotations.NotNull;

import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/** 统一解析数据库 Product 与应用提供的只读代码 Product。 */
public final class ProductDefinitionCatalog {

    private final @NotNull ProductRepository database;

    private final @NotNull Map<String, ProductDefinition> codeDefinitions;

    /** 创建拒绝重复 code 的双来源 Product 目录。 */
    public ProductDefinitionCatalog(
            @NotNull ProductRepository database,
            @NotNull Collection<ProductDefinition> codeDefinitions) {
        this.database = Objects.requireNonNull(database, "Product Repository 不能为空");
        LinkedHashMap<String, ProductDefinition> values = new LinkedHashMap<>();
        for (ProductDefinition definition : Objects.requireNonNull(codeDefinitions, "代码 Product 定义不能为空")) {
            ProductDefinition checked = Objects.requireNonNull(definition, "代码 Product 定义不能为空");
            if (values.putIfAbsent(checked.code(), checked) != null) {
                throw new IllegalArgumentException("代码 ProductDefinition code 重复: " + checked.code());
            }
        }
        this.codeDefinitions = Map.copyOf(values);
    }

    /**
     * 解析创建 Device 使用的 Product。
     *
     * @param selector 创建请求选择的 Product 来源和值
     * @return Device 应保存的稳定绑定和技术定义
     */
    @CheckReturnValue
    public @NotNull ProductResolution resolve(@NotNull ProductSelector selector) {
        ProductSelector input = Objects.requireNonNull(selector, "Product 选择不能为空");
        if (input.source() == ProductSource.CODE) {
            String code = Objects.requireNonNull(input.codeProductCode(), "代码 Product code 不能为空");
            ProductDefinition definition = codeDefinitions.get(code);
            if (definition == null) {
                throw new BusinessException(IotErrorCode.PRODUCT_NOT_FOUND);
            }
            ensureDatabaseCodeAbsent(code);
            return new ProductResolution(ProductBinding.code(code), definition);
        }
        long productId = Objects.requireNonNull(input.databaseProductId(), "数据库 Product 标识不能为空");
        Product product = database.find(productId)
                .orElseThrow(() -> new BusinessException(IotErrorCode.PRODUCT_NOT_FOUND));
        ensureCodeDefinitionAbsent(product.code());
        return new ProductResolution(
                ProductBinding.database(product.id(), product.code()),
                ProductDefinition.from(product));
    }

    /**
     * 解析已保存 Device 的 Product 绑定。
     *
     * @param binding Device 保存的 Product 来源、code 和可选数据库标识
     * @return 当前仍有效且与绑定一致的技术定义
     */
    @CheckReturnValue
    public @NotNull ProductDefinition definition(@NotNull ProductBinding binding) {
        ProductBinding input = Objects.requireNonNull(binding, "Product 绑定不能为空");
        if (input.source() == ProductSource.CODE) {
            ProductDefinition definition = codeDefinitions.get(input.code());
            if (definition == null) {
                throw new BusinessException(IotErrorCode.PRODUCT_NOT_FOUND);
            }
            ensureDatabaseCodeAbsent(input.code());
            return definition;
        }
        long productId = input.databaseProductId()
                .orElseThrow(() -> new IllegalArgumentException("数据库 Product 绑定缺少标识"));
        Product product = database.find(productId)
                .orElseThrow(() -> new BusinessException(IotErrorCode.PRODUCT_NOT_FOUND));
        if (!product.code().equals(input.code())) {
            throw new BusinessException(IotErrorCode.STATE_CONFLICT);
        }
        ensureCodeDefinitionAbsent(product.code());
        return ProductDefinition.from(product);
    }

    /**
     * 按稳定 code 查询数据库或代码 Product。
     *
     * @param code Product code
     * @return 来源、稳定绑定和技术定义；两种来源都不存在时为空
     */
    @CheckReturnValue
    public @NotNull Optional<ProductResolution> find(@NotNull String code) {
        String checked = Objects.requireNonNull(code, "Product code 不能为空");
        ProductDefinition codeDefinition = codeDefinitions.get(checked);
        if (codeDefinition != null) {
            ensureDatabaseCodeAbsent(checked);
            return Optional.of(new ProductResolution(ProductBinding.code(checked), codeDefinition));
        }
        return database.find(checked)
                .map(
                        product -> new ProductResolution(
                                ProductBinding.database(product.id(), product.code()),
                                ProductDefinition.from(product)));
    }

    /**
     * 查询数据库和代码提供的全部 Product。
     *
     * @return 按稳定 code 排列的不可修改目录
     */
    @CheckReturnValue
    public @NotNull List<ProductResolution> findAll() {
        LinkedHashMap<String, ProductResolution> values = new LinkedHashMap<>();
        for (Product product : database.findAll()) {
            values.put(
                    product.code(),
                    new ProductResolution(
                            ProductBinding.database(product.id(), product.code()),
                            ProductDefinition.from(product)));
        }
        codeDefinitions.forEach((code, definition) -> {
            if (values.putIfAbsent(
                    code,
                    new ProductResolution(ProductBinding.code(code), definition)) != null) {
                throw new BusinessException(IotErrorCode.IDENTITY_CONFLICT);
            }
        });
        return values.values()
                .stream()
                .sorted(Comparator.comparing(value -> value.binding().code()))
                .toList();
    }

    /**
     * 按稳定 code 分页查询数据库和代码 Product，不读取分页窗口之外的数据库聚合。
     *
     * @param page 从一开始且页大小不超过 100 的分页要求
     * @return 双来源统一排序的当前页和总数
     */
    @CheckReturnValue
    public @NotNull Page<ProductResolution> findPage(@NotNull PageRequest page) {
        // 先校验统一分页边界并计算双来源总数，越过末页时不执行数据库窗口查询。
        PageRequest checked = Objects.requireNonNull(page, "Product 目录分页要求不能为空").requireMaxSize(100);
        long databaseTotal = database.count();
        long total = Math.addExact(databaseTotal, codeDefinitions.size());
        long offset = checked.offset();
        if (offset >= total) {
            return new Page<>(List.of(), checked, total);
        }

        // 代码定义最多把数据库排序位置向后移动 codeDefinitions.size() 位，只读取覆盖该位移的数据库窗口。
        long databaseOffset = Math.max(0, offset - codeDefinitions.size());
        int databaseWindowSize = Math.addExact(checked.size(), codeDefinitions.size());
        LinkedHashMap<String, ProductResolution> values = new LinkedHashMap<>();
        for (Product product : database.findCodeWindow(databaseOffset, databaseWindowSize)) {
            values.put(
                    product.code(),
                    new ProductResolution(
                            ProductBinding.database(product.id(), product.code()),
                            ProductDefinition.from(product)));
        }
        codeDefinitions.forEach((code, definition) -> {
            if (values.putIfAbsent(
                    code,
                    new ProductResolution(ProductBinding.code(code), definition)) != null) {
                throw new BusinessException(IotErrorCode.IDENTITY_CONFLICT);
            }
        });

        // 按稳定 code 归并两个来源，再把归并窗口裁剪成调用方请求的精确页。
        List<ProductResolution> merged = values.values()
                .stream()
                .sorted(Comparator.comparing(value -> value.binding().code()))
                .toList();
        int from = Math.toIntExact(offset - databaseOffset);
        if (from >= merged.size()) {
            return new Page<>(List.of(), checked, total);
        }
        int to = Math.min(merged.size(), from + checked.size());
        return new Page<>(merged.subList(from, to), checked, total);
    }

    /** @return 指定 code 的代码 Product 定义 */
    @CheckReturnValue
    public @NotNull Optional<ProductDefinition> findCodeDefinition(@NotNull String code) {
        return Optional.ofNullable(codeDefinitions.get(Objects.requireNonNull(code, "Product code 不能为空")));
    }

    /** 拒绝数据库 Product 使用代码目录已经占用的 code。 */
    public void validateDatabaseCodeAvailable(@NotNull String code) {
        ensureCodeDefinitionAbsent(Objects.requireNonNull(code, "Product code 不能为空"));
    }

    /**
     * 检查已经存在的数据库 Product 没有占用代码 Product code。
     *
     * <p>应用应在数据库迁移完成后的启动阶段调用；发现冲突时拒绝启动。
     */
    public void validateNoCollisions() {
        codeDefinitions.keySet().forEach(this::ensureDatabaseCodeAbsent);
    }

    /** 拒绝数据库 Product 与代码 Product 共用同一 code。 */
    private void ensureCodeDefinitionAbsent(@NotNull String code) {
        if (codeDefinitions.containsKey(code)) {
            throw new BusinessException(IotErrorCode.IDENTITY_CONFLICT);
        }
    }

    /** 拒绝代码 Product 与已经保存的数据库 Product 共用 code。 */
    private void ensureDatabaseCodeAbsent(@NotNull String code) {
        if (database.find(code).isPresent()) {
            throw new BusinessException(IotErrorCode.IDENTITY_CONFLICT);
        }
    }
}
