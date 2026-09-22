package com.sca4d.iot.product;

import com.sca4d.core.api.persistence.AffectedRowCounts;
import com.sca4d.core.api.persistence.AffectedRows;
import com.sca4d.core.api.persistence.AffectedRowsMismatchException;
import com.sca4d.core.api.persistence.ZeroRowPolicy;
import com.sca4d.core.api.query.Page;
import com.sca4d.core.api.query.PageRequest;
import com.sca4d.core.spring.persistence.batch.SqlBatches;
import com.sca4d.iot.api.DeviceAccessMethod;
import com.sca4d.iot.api.Product;
import com.sca4d.iot.persistence.ProductAccessMethodState;
import com.sca4d.iot.persistence.ProductProtocolState;
import com.sca4d.iot.persistence.ProductState;
import com.sca4d.iot.persistence.mapper.ProductMapper;
import org.jetbrains.annotations.CheckReturnValue;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Unmodifiable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/** 把 Product 持久化状态和集合表转换成完整 Product，并检查写入结果。 */
public class ProductRepository {

    private final @NotNull ProductMapper mapper;

    private final @NotNull SqlBatches sqlBatches;

    private final int scanBatchSize;

    /**
     * 创建 Product 持久化入口。
     *
     * @param mapper Product SQL Mapper
     * @param sqlBatches SQL 批处理器
     * @param scanBatchSize 读取完整 Product 目录时的正数分页大小
     */
    public ProductRepository(
            @NotNull ProductMapper mapper,
            @NotNull SqlBatches sqlBatches,
            int scanBatchSize) {
        this.mapper = Objects.requireNonNull(mapper, "Product Mapper 不能为空");
        this.sqlBatches = Objects.requireNonNull(sqlBatches, "SQL 批处理器不能为空");
        if (scanBatchSize < 1) {
            throw new IllegalArgumentException("Product 目录扫描批量必须为正数");
        }
        this.scanBatchSize = scanBatchSize;
    }

    /**
     * 保存 Product 及其允许接入方式和兼容协议。
     *
     * @param product 待保存的完整 Product
     * @throws AffectedRowsMismatchException 任一必要写入没有准确影响预期行数时抛出
     */
    public void add(@NotNull Product product) {
        Product checked = Objects.requireNonNull(product, "Product 不能为空");
        AffectedRowCounts.requireSingle(
                mapper.insertProduct(checked.id(), checked.code(), checked.i18n()),
                ZeroRowPolicy.ERROR,
                "新增 Product");
        List<String> accessMethods = checked.accessMethods().stream().map(DeviceAccessMethod::code).toList();
        sqlBatches.forEach(
                accessMethods,
                2,
                0,
                batch -> AffectedRowCounts.requireExactly(
                        mapper.insertProductAccessMethods(checked.id(), batch),
                        batch.size(),
                        "新增 Product 接入方式"));
        List<String> protocols = List.copyOf(checked.protocolCodes());
        sqlBatches.forEach(
                protocols,
                2,
                0,
                batch -> AffectedRowCounts.requireExactly(
                        mapper.insertProductProtocols(checked.id(), batch),
                        batch.size(),
                        "新增 Product 协议"));
    }

    /**
     * 按标识查询完整 Product。
     *
     * @param id Product 标识
     * @return 匹配的 Product；不存在时为空
     */
    @CheckReturnValue
    public @NotNull Optional<Product> find(long id) {
        return hydrate(mapper.selectProducts(id, null, 0, 1, false)).stream().findFirst();
    }

    /**
     * 按编码查询完整 Product。
     *
     * @param code Product 编码
     * @return 匹配的 Product；不存在时为空
     */
    @CheckReturnValue
    public @NotNull Optional<Product> find(@NotNull String code) {
        return hydrate(mapper.selectProducts(null, Objects.requireNonNull(code, "Product 编码不能为空"), 0, 1, false))
                .stream()
                .findFirst();
    }

    /**
     * 查询全部完整 Product。
     *
     * @return 按创建时间和标识排列的不可修改列表
     */
    @CheckReturnValue
    public @NotNull @Unmodifiable List<Product> findAll() {
        List<Product> result = new ArrayList<>();
        for (long offset = 0;; offset += scanBatchSize) {
            List<ProductState> batch = mapper.selectProducts(null, null, offset, scanBatchSize, false);
            result.addAll(hydrate(batch));
            if (batch.size() < scanBatchSize) {
                return List.copyOf(result);
            }
        }
    }

    /**
     * 分页查询完整 Product，关联集合始终按当前页批量读取。
     *
     * @param page 从一开始且页大小不超过 100 的分页要求
     * @return 当前页完整 Product 和总数
     */
    @CheckReturnValue
    public @NotNull Page<Product> findPage(@NotNull PageRequest page) {
        PageRequest checked = Objects.requireNonNull(page, "Product 分页要求不能为空").verifyMaxSize(100);
        long total = mapper.countProducts();
        List<ProductState> rows = total == 0
                ? List.of()
                : mapper.selectProducts(null, null, checked.offset(), checked.size(), false);
        return new Page<>(hydrate(rows), checked, total);
    }

    /** @return 数据库 Product 总数，供双来源目录计算统一分页 */
    @CheckReturnValue
    long count() {
        return mapper.countProducts();
    }

    /**
     * 按 code 查询一个数据库 Product 窗口，只批量装载窗口内的关联集合。
     *
     * @param offset 按 code 排序后跳过数量
     * @param size 最大返回数量
     * @return 按 code 排列的完整 Product
     */
    @CheckReturnValue
    @NotNull
    List<Product> findCodeWindow(long offset, int size) {
        if (offset < 0 || size < 1) {
            throw new IllegalArgumentException("Product code 窗口参数不合法");
        }
        return hydrate(mapper.selectProducts(null, null, offset, size, true));
    }

    /**
     * 完整替换 Product 允许接入方式和兼容协议。
     *
     * @param product 修改后的完整 Product
     * @throws AffectedRowsMismatchException Product 不存在或集合写入没有准确影响预期行数时抛出
     */
    public void replace(@NotNull Product product) {
        Product checked = Objects.requireNonNull(product, "Product 不能为空");
        AffectedRowCounts.requireSingle(
                mapper.touchProduct(checked.id()),
                ZeroRowPolicy.ERROR,
                "推进 Product 更新时间");
        mapper.deleteProductAccessMethods(checked.id());
        List<String> accessMethods = checked.accessMethods().stream().map(DeviceAccessMethod::code).toList();
        sqlBatches.forEach(
                accessMethods,
                2,
                0,
                batch -> AffectedRowCounts.requireExactly(
                        mapper.insertProductAccessMethods(checked.id(), batch),
                        batch.size(),
                        "新增 Product 接入方式"));
        mapper.deleteProductProtocols(checked.id());
        List<String> protocols = List.copyOf(checked.protocolCodes());
        sqlBatches.forEach(
                protocols,
                2,
                0,
                batch -> AffectedRowCounts.requireExactly(
                        mapper.insertProductProtocols(checked.id(), batch),
                        batch.size(),
                        "新增 Product 协议"));
    }

    /** 分批加载 Product 的两类关系，并按原顺序组装完整领域对象。 */
    private @NotNull List<Product> hydrate(@NotNull List<ProductState> rows) {
        List<ProductState> checked = List.copyOf(rows);
        if (checked.isEmpty()) {
            return List.of();
        }

        // 对当前批次分别读取接入方式和协议，数据库往返次数不随 Product 数量增加。
        List<Long> ids = checked.stream().map(ProductState::id).toList();
        Map<Long, List<DeviceAccessMethod>> accessMethods = new HashMap<>();
        for (ProductAccessMethodState state : sqlBatches.query(
                ids,
                1,
                0,
                mapper::selectProductAccessMethodsBatch)) {
            accessMethods.computeIfAbsent(state.productId(), ignored -> new ArrayList<>())
                    .add(DeviceAccessMethod.parse(state.methodCode()));
        }
        Map<Long, List<String>> protocols = new LinkedHashMap<>();
        for (ProductProtocolState state : sqlBatches.query(
                ids,
                1,
                0,
                mapper::selectProductProtocolsBatch)) {
            protocols.computeIfAbsent(state.productId(), ignored -> new ArrayList<>())
                    .add(state.protocolCode());
        }
        // 按标量查询的原顺序组装 Product，缺少关联行时使用空集合。
        return checked.stream()
                .map(
                        row -> new Product(
                                row.id(),
                                row.code(),
                                row.i18n(),
                                accessMethods.getOrDefault(row.id(), List.of()),
                                protocols.getOrDefault(row.id(), List.of())))
                .toList();
    }

    /** 完整替换 Product 多语言展示字段。 */
    @AffectedRows(target = "Product 多语言字段")
    public void replaceI18n(@NotNull Product product) {
        Product checked = Objects.requireNonNull(product, "Product 不能为空");
        mapper.updateProductI18n(checked.id(), checked.i18n());
    }
}
