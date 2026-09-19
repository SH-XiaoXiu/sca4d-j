package com.sca4d.iot.product;

import com.sca4d.core.api.concurrent.DistributedLock;
import com.sca4d.core.api.concurrent.LockKey;
import com.sca4d.core.api.error.BusinessException;
import com.sca4d.core.api.i18n.LocaleCatalog;
import com.sca4d.core.api.i18n.LocalizedFields;
import com.sca4d.core.api.i18n.LocalizedFieldsSchema;
import com.sca4d.core.api.id.IdGenerator;
import com.sca4d.core.api.query.Page;
import com.sca4d.core.api.query.PageRequest;
import com.sca4d.iot.IotErrorCode;
import com.sca4d.iot.api.Product;
import com.sca4d.iot.api.model.ThingModelPresentation;
import com.sca4d.iot.api.model.ThingModelRepository;
import com.sca4d.iot.device.DeviceRepository;
import com.sca4d.iot.protocol.DeviceProtocolCatalog;
import org.jetbrains.annotations.CheckReturnValue;
import org.jetbrains.annotations.NotNull;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Supplier;

/** 管理公共 Product 技术定义，不保存具体 App 的启停状态。 */
public class ProductService {

    private static final Duration LOCK_WAIT = Duration.ofSeconds(10);

    private static final LocalizedFieldsSchema I18N_SCHEMA = new LocalizedFieldsSchema(
            Set.of("displayName"),
            Set.of("displayName"),
            Map.of("displayName", 128));

    private final @NotNull ProductRepository products;

    private final @NotNull ProductDefinitionCatalog definitions;

    private final @NotNull DeviceRepository devices;

    private final @NotNull DeviceProtocolCatalog protocols;

    private final @NotNull ThingModelRepository models;

    private final @NotNull IdGenerator ids;

    private final @NotNull LocaleCatalog locales;

    private final @NotNull DistributedLock distributedLock;

    private final @NotNull TransactionTemplate transactions;

    /** 创建 Product 服务。 */
    public ProductService(
            @NotNull ProductRepository products,
            @NotNull ProductDefinitionCatalog definitions,
            @NotNull DeviceRepository devices,
            @NotNull DeviceProtocolCatalog protocols,
            @NotNull ThingModelRepository models,
            @NotNull IdGenerator ids,
            @NotNull LocaleCatalog locales,
            @NotNull DistributedLock distributedLock,
            @NotNull TransactionTemplate transactions) {
        this.products = Objects.requireNonNull(products, "Product Repository 不能为空");
        this.definitions = Objects.requireNonNull(definitions, "ProductDefinition 目录不能为空");
        this.devices = Objects.requireNonNull(devices, "Device Repository 不能为空");
        this.protocols = Objects.requireNonNull(protocols, "协议目录不能为空");
        this.models = Objects.requireNonNull(models, "ThingModelRepository 不能为空");
        this.ids = Objects.requireNonNull(ids, "ID 生成器不能为空");
        this.locales = Objects.requireNonNull(locales, "Locale 目录不能为空");
        this.distributedLock = Objects.requireNonNull(distributedLock, "分布式锁不能为空");
        this.transactions = Objects.requireNonNull(transactions, "事务模板不能为空");
    }

    /** @param registration 完整 Product 创建数据 @return 新建 Product */
    @CheckReturnValue
    public @NotNull Product create(@NotNull ProductRegistration registration) {
        ProductRegistration input = Objects.requireNonNull(registration, "Product 创建数据不能为空");
        return write(input.code(), () -> {
            definitions.validateDatabaseCodeAvailable(input.code());
            input.protocolCodes().forEach(protocols::loadByCode);
            Product product = new Product(
                    ids.nextId(),
                    input.code(),
                    validateI18n(input.i18n()),
                    input.accessMethods(),
                    input.protocolCodes());
            try {
                products.add(product);
            } catch (DuplicateKeyException exception) {
                throw new BusinessException(IotErrorCode.IDENTITY_CONFLICT, exception);
            }
            return product;
        });
    }

    /**
     * 完整替换 Product 技术定义；仍被 Device 使用的接入方式或协议不能移除。
     *
     * @param productId Product 标识
     * @param change 新的完整接入方式和协议集合
     */
    public void update(long productId, @NotNull ProductChange change) {
        Product existing = products.find(productId)
                .orElseThrow(() -> new BusinessException(IotErrorCode.PRODUCT_NOT_FOUND));
        ProductChange input = Objects.requireNonNull(change, "Product 修改数据不能为空");
        write(existing.code(), () -> {
            Product current = products.find(productId)
                    .orElseThrow(() -> new BusinessException(IotErrorCode.PRODUCT_NOT_FOUND));
            Product updated = new Product(
                    current.id(),
                    current.code(),
                    current.i18n(),
                    input.accessMethods(),
                    input.protocolCodes());

            // 已登记 Device 不自动改变接入方式或协议，Product 不能删除仍被引用的值。
            if (devices.existsIncompatibleProductUsage(
                    productId,
                    updated.accessMethods(),
                    updated.protocolCodes())) {
                throw new BusinessException(IotErrorCode.STATE_CONFLICT);
            }

            // 新增协议必须能够解释 Product 已经发布的每一个物模型版本，同一批模型只读取一次。
            List<ThingModelPresentation> publishedModels = models.findPublished(current.code());
            try {
                updated.protocolCodes()
                        .forEach(
                                code -> publishedModels.forEach(
                                        model -> protocols.loadByCode(code).validate(model.model())));
            } catch (IllegalArgumentException exception) {
                throw new BusinessException(IotErrorCode.PROTOCOL_CONFLICT, exception);
            }
            products.replace(updated);
        });
    }

    /**
     * 完整替换 Product 的多语言展示字段，不改变技术定义或已启用 Device。
     *
     * @param productId Product 标识
     * @param i18n 新的完整多语言展示字段
     * @return 修改后的 Product
     */
    @CheckReturnValue
    public @NotNull Product replaceI18n(long productId, @NotNull LocalizedFields i18n) {
        Product existing = products.find(productId)
                .orElseThrow(() -> new BusinessException(IotErrorCode.PRODUCT_NOT_FOUND));
        return write(existing.code(), () -> {
            Product current = products.find(productId)
                    .orElseThrow(() -> new BusinessException(IotErrorCode.PRODUCT_NOT_FOUND));
            Product updated = new Product(
                    current.id(),
                    current.code(),
                    validateI18n(i18n),
                    current.accessMethods(),
                    current.protocolCodes());
            products.replaceI18n(updated);
            return updated;
        });
    }

    /** @return 全部 Product */
    @Transactional(readOnly = true)
    @CheckReturnValue
    public @NotNull List<Product> findAll() {
        return products.findAll();
    }

    /** @param page 从一开始且页大小不超过 100 的分页要求 @return Product 分页结果 */
    @Transactional(readOnly = true)
    @CheckReturnValue
    public @NotNull Page<Product> findPage(@NotNull PageRequest page) {
        return products.findPage(page);
    }

    /** @param productId Product 标识 @return 必须存在的 Product */
    @Transactional(readOnly = true)
    @CheckReturnValue
    public @NotNull Product loadById(long productId) {
        return products.find(productId)
                .orElseThrow(() -> new BusinessException(IotErrorCode.PRODUCT_NOT_FOUND));
    }

    /** 取得 Product 聚合锁后，在事务内执行有返回值的写操作。 */
    private <T> @NotNull T write(@NotNull String productCode, @NotNull Supplier<@NotNull T> operation) {
        return distributedLock.execute(
                new LockKey("iot", "product", productCode),
                LOCK_WAIT,
                () -> Objects.requireNonNull(
                        transactions.execute(status -> operation.get()),
                        "Product 写操作结果不能为空"));
    }

    /** 取得 Product 聚合锁后，在事务内执行无返回值的写操作。 */
    private void write(@NotNull String productCode, @NotNull Runnable operation) {
        distributedLock.execute(
                new LockKey("iot", "product", productCode),
                LOCK_WAIT,
                () -> {
                    transactions.executeWithoutResult(status -> operation.run());
                    return null;
                });
    }

    /** 按应用默认 Locale 校验 Product 的完整多语言展示字段。 */
    private @NotNull LocalizedFields validateI18n(@NotNull LocalizedFields i18n) {
        try {
            return I18N_SCHEMA.validate(i18n, locales.defaultLocale());
        } catch (IllegalArgumentException exception) {
            throw new BusinessException(IotErrorCode.INVALID_INPUT, exception);
        }
    }
}
