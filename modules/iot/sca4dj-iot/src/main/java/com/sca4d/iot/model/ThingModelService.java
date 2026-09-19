package com.sca4d.iot.model;

import com.sca4d.core.api.concurrent.DistributedLock;
import com.sca4d.core.api.concurrent.LockKey;
import com.sca4d.core.api.error.BusinessException;
import com.sca4d.core.api.i18n.LocalizedFields;
import com.sca4d.iot.IotErrorCode;
import com.sca4d.iot.api.Product;
import com.sca4d.iot.api.model.ThingCommand;
import com.sca4d.iot.api.model.ThingModel;
import com.sca4d.iot.api.model.ThingModelPresentation;
import com.sca4d.iot.api.model.ThingModelRepository;
import com.sca4d.iot.api.model.ThingProperty;
import com.sca4d.iot.message.DevicePolicyCatalog;
import com.sca4d.iot.product.ProductRepository;
import com.sca4d.iot.protocol.DeviceProtocolCatalog;
import org.jetbrains.annotations.CheckReturnValue;
import org.jetbrains.annotations.NotNull;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.util.HexFormat;
import java.util.List;
import java.util.Objects;

/** 在发布前校验 Product、策略和全部兼容协议，再调用 App 唯一选择的 Repository。 */
public class ThingModelService {

    private static final Duration LOCK_WAIT = Duration.ofSeconds(10);

    private final @NotNull ThingModelRepository models;

    private final @NotNull ProductRepository products;

    private final @NotNull DevicePolicyCatalog policies;

    private final @NotNull DeviceProtocolCatalog protocols;

    private final @NotNull ThingModelJsonCodec codec;

    private final @NotNull ThingModelI18nValidator i18nValidator;

    private final @NotNull DistributedLock distributedLock;

    private final @NotNull TransactionTemplate transactions;

    /** 创建物模型发布服务。 */
    public ThingModelService(
            @NotNull ThingModelRepository models,
            @NotNull ProductRepository products,
            @NotNull DevicePolicyCatalog policies,
            @NotNull DeviceProtocolCatalog protocols,
            @NotNull ThingModelJsonCodec codec,
            @NotNull ThingModelI18nValidator i18nValidator,
            @NotNull DistributedLock distributedLock,
            @NotNull TransactionTemplate transactions) {
        this.models = Objects.requireNonNull(models, "ThingModelRepository 不能为空");
        this.products = Objects.requireNonNull(products, "Product Repository 不能为空");
        this.policies = Objects.requireNonNull(policies, "设备策略目录不能为空");
        this.protocols = Objects.requireNonNull(protocols, "设备协议目录不能为空");
        this.codec = Objects.requireNonNull(codec, "物模型 JSON Codec 不能为空");
        this.i18nValidator = Objects.requireNonNull(i18nValidator, "物模型翻译校验器不能为空");
        this.distributedLock = Objects.requireNonNull(distributedLock, "分布式锁不能为空");
        this.transactions = Objects.requireNonNull(transactions, "事务模板不能为空");
    }

    /** @param model 待发布物模型 @return 发布或幂等恢复的物模型 */
    @CheckReturnValue
    public @NotNull ThingModelPresentation publish(
            @NotNull ThingModel model,
            @NotNull LocalizedFields i18n) {
        ThingModel checked = Objects.requireNonNull(model, "物模型不能为空");
        LocalizedFields checkedI18n;

        // 默认 Locale 必须完整覆盖技术定义要求的全部展示路径，其他 Locale 不得增加未知路径。
        try {
            checkedI18n = i18nValidator.validate(checked, i18n);
        } catch (IllegalArgumentException exception) {
            throw new BusinessException(IotErrorCode.INVALID_INPUT, exception);
        }

        return write(checked.productCode(), () -> {
            Product product = products.find(checked.productCode())
                    .orElseThrow(() -> new BusinessException(IotErrorCode.PRODUCT_NOT_FOUND));
            checked.properties().values().forEach(this::validatePropertyPolicies);
            checked.commands().values().forEach(this::validateCommandPolicies);
            try {
                product.protocolCodes().forEach(code -> protocols.loadByCode(code).validate(checked));
            } catch (IllegalArgumentException exception) {
                throw new BusinessException(IotErrorCode.PROTOCOL_CONFLICT, exception);
            }
            // 相同版本、相同技术内容按幂等结果返回；其他版本必须严格连续且内容不能重复。
            String hash = sha256(codec.encode(checked));
            List<ThingModelPresentation> published = models.findPublished(checked.productCode());
            if (published.isEmpty()) {
                if (checked.version() != 1) {
                    throw new BusinessException(IotErrorCode.THING_MODEL_CONFLICT);
                }
            } else {
                ThingModel latest = published.getLast().model();
                if (checked.version() == latest.version()) {
                    if (hash.equals(sha256(codec.encode(latest)))) {
                        return published.getLast();
                    }
                    throw new BusinessException(IotErrorCode.THING_MODEL_CONFLICT);
                }
                if (checked.version() != latest.version() + 1
                        || hash.equals(sha256(codec.encode(latest)))) {
                    throw new BusinessException(IotErrorCode.THING_MODEL_CONFLICT);
                }
            }
            return models.publish(checked, checkedI18n);
        });
    }

    /** @param productCode Product code @param version 物模型版本 @return 必须存在的物模型展示结果 */
    @Transactional(readOnly = true)
    @CheckReturnValue
    public @NotNull ThingModelPresentation loadByProductVersion(@NotNull String productCode, int version) {
        return models.find(productCode, version)
                .orElseThrow(() -> new BusinessException(IotErrorCode.THING_MODEL_NOT_FOUND));
    }

    /**
     * 完整替换一个已发布物模型版本的动态翻译，不改变技术定义或版本。
     *
     * @return 修改后的物模型展示结果
     */
    @CheckReturnValue
    public @NotNull ThingModelPresentation replaceI18n(
            @NotNull String productCode,
            int version,
            @NotNull LocalizedFields i18n) {
        return write(productCode, () -> {
            ThingModelPresentation current = loadByProductVersion(productCode, version);
            LocalizedFields checked;
            try {
                checked = i18nValidator.validate(current.model(), i18n);
            } catch (IllegalArgumentException exception) {
                throw new BusinessException(IotErrorCode.INVALID_INPUT, exception);
            }
            return models.replaceI18n(productCode, version, checked);
        });
    }

    /** 取得 Product 聚合锁后，在事务内发布或修改物模型。 */
    private <T> @NotNull T write(
            @NotNull String productCode,
            @NotNull java.util.function.Supplier<@NotNull T> operation) {
        return distributedLock.execute(
                new LockKey("iot", "product", productCode),
                LOCK_WAIT,
                () -> Objects.requireNonNull(
                        transactions.execute(status -> operation.get()),
                        "物模型写操作结果不能为空"));
    }

    /** 校验一个可写属性引用的策略。 */
    private void validatePropertyPolicies(ThingProperty property) {
        property.acknowledgementPolicy().ifPresent(policy -> policies.acknowledgement(policy.code()));
        property.retryPolicy().ifPresent(policy -> policies.retry(policy.code()));
    }

    /** 校验一个命令引用的策略。 */
    private void validateCommandPolicies(ThingCommand command) {
        policies.acknowledgement(command.acknowledgementPolicy().code());
        policies.retry(command.retryPolicy().code());
    }

    /** 计算规范 JSON 的小写 SHA-256 摘要，用于版本幂等和连续性判断。 */
    private static @NotNull String sha256(@NotNull String value) {
        try {
            return HexFormat.of()
                    .formatHex(
                            MessageDigest.getInstance("SHA-256")
                                    .digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("JVM 不支持 SHA-256", exception);
        }
    }
}
