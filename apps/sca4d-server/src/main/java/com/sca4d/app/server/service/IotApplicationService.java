package com.sca4d.app.server.service;

import static com.sca4d.core.api.name.NameFormat.COLON;

import cn.hutool.crypto.digest.DigestUtil;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sca4d.app.server.iot.DeviceCreationReplay;
import com.sca4d.app.server.iot.DeviceCreationRequestRecord;
import com.sca4d.app.server.iot.DeviceCredentialService;
import com.sca4d.app.server.iot.IotDeviceCredentials;
import com.sca4d.app.server.iot.IotDeviceDetails;
import com.sca4d.app.server.iot.IotDeviceProfile;
import com.sca4d.app.server.iot.IotProductCatalogItem;
import com.sca4d.app.server.iot.IotProductDetails;
import com.sca4d.app.server.iot.IotProductProfile;
import com.sca4d.app.server.iot.IotProfileStatus;
import com.sca4d.app.server.repository.IotApplicationRepository;
import com.sca4d.auth.hmac.IssuedHmacKey;
import com.sca4d.auth.sharedsecret.IssuedSharedSecret;
import com.sca4d.auth.x509.X509CredentialRegistration;
import com.sca4d.core.api.authentication.SessionControl;
import com.sca4d.core.api.authentication.SessionTerminationReason;
import com.sca4d.core.api.concurrent.DistributedLock;
import com.sca4d.core.api.concurrent.LockKey;
import com.sca4d.core.api.data.DataOps;
import com.sca4d.core.api.error.BusinessException;
import com.sca4d.core.api.i18n.LocalizedFields;
import com.sca4d.core.api.identity.Account;
import com.sca4d.core.api.identity.Subject;
import com.sca4d.core.api.identity.SubjectType;
import com.sca4d.core.api.name.code.TreeCode;
import com.sca4d.core.api.query.ListRequest;
import com.sca4d.core.api.query.Page;
import com.sca4d.core.api.query.PageRequest;
import com.sca4d.core.api.query.Sort;
import com.sca4d.core.api.security.EncryptedSecret;
import com.sca4d.core.api.security.SecretCipher;
import com.sca4d.core.api.security.SecretKeyring;
import com.sca4d.iam.identity.IdentityService;
import com.sca4d.iam.identity.IdentityStatus;
import com.sca4d.iot.IotErrorCode;
import com.sca4d.iot.api.*;
import com.sca4d.iot.api.Product;
import com.sca4d.iot.api.ProductSource;
import com.sca4d.iot.api.change.DeviceChangeArea;
import com.sca4d.iot.api.connection.DeviceConnectionBinding;
import com.sca4d.iot.api.message.DeviceDownlink;
import com.sca4d.iot.api.model.ThingModel;
import com.sca4d.iot.api.model.ThingModelPresentation;
import com.sca4d.iot.api.model.ThingModelRepository;
import com.sca4d.iot.api.protocol.DeviceProtocol;
import com.sca4d.iot.change.DeviceChangeNotifier;
import com.sca4d.iot.config.IotProperties;
import com.sca4d.iot.device.DeviceBindingChange;
import com.sca4d.iot.device.DeviceConnectionChange;
import com.sca4d.iot.device.DeviceDetailsChange;
import com.sca4d.iot.device.DeviceQuery;
import com.sca4d.iot.device.DeviceRegistration;
import com.sca4d.iot.device.DeviceService;
import com.sca4d.iot.message.DesiredStateChange;
import com.sca4d.iot.message.DesiredStateResult;
import com.sca4d.iot.message.DesiredStateService;
import com.sca4d.iot.message.DeviceCommandService;
import com.sca4d.iot.model.ThingModelJsonCodec;
import com.sca4d.iot.model.ThingModelService;
import com.sca4d.iot.product.ProductChange;
import com.sca4d.iot.product.ProductDefinitionCatalog;
import com.sca4d.iot.product.ProductRegistration;
import com.sca4d.iot.product.ProductResolution;
import com.sca4d.iot.product.ProductService;
import com.sca4d.iot.protocol.DeviceProtocolCatalog;
import org.jetbrains.annotations.CheckReturnValue;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Supplier;

/** 编排 Sca4d Server 的 Product、Device、App Profile 和 IAM 启停事务。 */
@Service
public class IotApplicationService {

    private static final Logger LOG = LoggerFactory.getLogger(IotApplicationService.class);

    private static final Duration DEVICE_CREATION_REPLAY_WINDOW = Duration.ofMinutes(10);

    private static final String DEVICE_CREATION_PURPOSE_PREFIX = "app-iot-device-create:";

    private static final Duration LOCK_WAIT = Duration.ofSeconds(10);

    private final @NotNull ProductService products;

    private final @NotNull ProductDefinitionCatalog productDefinitions;

    private final @NotNull DeviceService devices;

    private final @NotNull ThingModelService thingModels;

    private final @NotNull DesiredStateService desiredStates;

    private final @NotNull DeviceCommandService deviceCommands;

    private final @NotNull ThingModelRepository thingModelRepository;

    private final @NotNull ThingModelJsonCodec thingModelJson;

    private final @NotNull DeviceProtocolCatalog protocols;

    private final @NotNull IdentityService identities;

    private final @NotNull SessionControl sessions;

    private final @NotNull DeviceCredentialService credentials;

    private final @NotNull IotApplicationRepository repository;

    private final @NotNull SecretKeyring keyring;

    private final @NotNull ObjectMapper objectMapper;

    private final @NotNull Clock clock;

    private final @NotNull DeviceChangeNotifier changes;

    private final @NotNull IotProperties iotProperties;

    private final @NotNull DistributedLock distributedLock;

    private final @NotNull TransactionTemplate transactions;

    /** 创建 Sca4d Server 的完整 IoT 应用事务入口。 */
    @SuppressWarnings("ParameterNumber")
    public IotApplicationService(
            @NotNull ProductService products,
            @NotNull ProductDefinitionCatalog productDefinitions,
            @NotNull DeviceService devices,
            @NotNull ThingModelService thingModels,
            @NotNull DesiredStateService desiredStates,
            @NotNull DeviceCommandService deviceCommands,
            @NotNull ThingModelRepository thingModelRepository,
            @NotNull ThingModelJsonCodec thingModelJson,
            @NotNull DeviceProtocolCatalog protocols,
            @NotNull IdentityService identities,
            @NotNull DeviceCredentialService credentials,
            @NotNull SessionControl sessions,
            @NotNull IotApplicationRepository repository,
            @NotNull SecretKeyring keyring,
            @NotNull ObjectMapper objectMapper,
            @NotNull Clock clock,
            @NotNull DeviceChangeNotifier changes,
            @NotNull IotProperties iotProperties,
            @NotNull DistributedLock distributedLock,
            @NotNull PlatformTransactionManager transactionManager) {
        this.products = Objects.requireNonNull(products, "ProductService 不能为空");
        this.productDefinitions = Objects.requireNonNull(productDefinitions, "ProductDefinition 目录不能为空");
        this.devices = Objects.requireNonNull(devices, "DeviceService 不能为空");
        this.thingModels = Objects.requireNonNull(thingModels, "ThingModelService 不能为空");
        this.desiredStates = Objects.requireNonNull(desiredStates, "DesiredStateService 不能为空");
        this.deviceCommands = Objects.requireNonNull(deviceCommands, "DeviceCommandService 不能为空");
        this.thingModelRepository = Objects.requireNonNull(thingModelRepository, "ThingModelRepository 不能为空");
        this.thingModelJson = Objects.requireNonNull(thingModelJson, "ThingModel JSON Codec 不能为空");
        this.protocols = Objects.requireNonNull(protocols, "DeviceProtocolCatalog 不能为空");
        this.identities = Objects.requireNonNull(identities, "IdentityService 不能为空");
        this.credentials = Objects.requireNonNull(credentials, "Device Credential 服务不能为空");
        this.sessions = Objects.requireNonNull(sessions, "Session 控制器不能为空");
        this.repository = Objects.requireNonNull(repository, "IoT 应用 Repository 不能为空");
        this.keyring = Objects.requireNonNull(keyring, "SecretKeyring 不能为空");
        this.objectMapper = Objects.requireNonNull(objectMapper, "ObjectMapper 不能为空");
        this.clock = Objects.requireNonNull(clock, "IoT 应用时钟不能为空");
        this.changes = Objects.requireNonNull(changes, "Device 变化通知器不能为空");
        this.iotProperties = Objects.requireNonNull(iotProperties, "IoT 配置不能为空");
        this.distributedLock = Objects.requireNonNull(distributedLock, "IoT 应用分布式锁不能为空");
        this.transactions = new TransactionTemplate(
                Objects.requireNonNull(transactionManager, "IoT 应用事务管理器不能为空"));
    }

    /** 创建公共 Product 和默认停用的 App Profile。 */
    @CheckReturnValue
    public @NotNull Product createProduct(@NotNull ProductRegistration registration) {
        ProductRegistration input = Objects.requireNonNull(registration, "Product 创建数据不能为空");
        return writeProduct(input.code(), () -> {
            Product product = products.create(input);
            repository.addProductProfile(product.id());
            LOG.debug(
                    "IoT Product 创建数据已写入事务 | productId={} code={}",
                    product.id(),
                    product.code());
            return product;
        });
    }

    /** 只在 Product App Profile 停用时修改公共技术定义。 */
    public void updateProduct(long productId, @NotNull ProductChange change) {
        String productCode = products.loadById(productId).code();
        writeProduct(productCode, () -> {
            assertProductDisabled(productId);
            products.update(productId, Objects.requireNonNull(change, "Product 修改数据不能为空"));
        });
    }

    /**
     * 完整替换 Product 展示翻译，不改变技术定义或 App 启停状态。
     *
     * @param productId Product 标识
     * @param i18n 新的完整多语言字段
     * @return 修改后的 Product
     */
    @CheckReturnValue
    public @NotNull Product replaceProductI18n(
            long productId,
            @NotNull LocalizedFields i18n) {
        String productCode = products.loadById(productId).code();
        return writeProduct(productCode, () -> {
            long checkedProductId = repository.findProductProfile(productId)
                    .orElseThrow(() -> new BusinessException(IotErrorCode.PRODUCT_NOT_FOUND))
                    .productId();
            Product product = products.replaceI18n(
                    checkedProductId,
                    Objects.requireNonNull(i18n, "Product 多语言字段不能为空"));
            LOG.debug("IoT Product 多语言字段已写入事务 | productId={}", checkedProductId);
            return product;
        });
    }

    /**
     * 发布管理端提交的 ThingModel 技术定义和初始完整多语言字段。
     *
     * @param productId 数据库 Product 标识
     * @param modelJson ThingModel 格式 2 技术定义
     * @param i18n 初始完整多语言字段
     * @return 发布成功或幂等命中的 ThingModel 展示结果
     */
    @CheckReturnValue
    public @NotNull ThingModelPresentation publishThingModel(
            long productId,
            @NotNull JsonNode modelJson,
            @NotNull LocalizedFields i18n) {
        ThingModel model = thingModelJson.decode(
                Objects.requireNonNull(modelJson, "ThingModel 技术定义不能为空").toString());
        Product product = products.loadById(productId);
        return writeProduct(product.code(), () -> {
            if (!product.code().equals(model.productCode())) {
                throw new BusinessException(IotErrorCode.INVALID_INPUT);
            }
            assertProductDisabled(productId);
            ThingModelPresentation published = thingModels.publish(
                    model,
                    Objects.requireNonNull(i18n, "ThingModel 多语言字段不能为空"));
            LOG.debug(
                    "IoT ThingModel 发布数据已写入事务 | productId={} productCode={} version={}",
                    productId,
                    published.model().productCode(),
                    published.model().version());
            return published;
        });
    }

    /** @return Product 按版本递增的全部已发布 ThingModel */
    @Transactional(readOnly = true)
    @CheckReturnValue
    public @NotNull List<ThingModelPresentation> findThingModels(long productId) {
        Product product = productById(productId).product();
        return thingModelRepository.findPublished(product.code());
    }

    /** @return Product 指定版本的 ThingModel */
    @Transactional(readOnly = true)
    @CheckReturnValue
    public @NotNull ThingModelPresentation thingModelByProductVersion(
            long productId,
            int version) {
        Product product = productById(productId).product();
        return thingModels.loadByProductVersion(product.code(), version);
    }

    /**
     * 完整替换一个已发布 ThingModel 版本的展示翻译。
     *
     * @param productId 数据库 Product 标识
     * @param version ThingModel 版本
     * @param i18n 新的完整多语言字段
     * @return 修改后的 ThingModel 展示结果
     */
    @CheckReturnValue
    public @NotNull ThingModelPresentation replaceThingModelI18n(
            long productId,
            int version,
            @NotNull LocalizedFields i18n) {
        Product product = products.loadById(productId);
        return writeProduct(product.code(), () -> {
            long checkedProductId = repository.findProductProfile(productId)
                    .orElseThrow(() -> new BusinessException(IotErrorCode.PRODUCT_NOT_FOUND))
                    .productId();
            ThingModelPresentation result = thingModels.replaceI18n(
                    product.code(),
                    version,
                    Objects.requireNonNull(i18n, "ThingModel 多语言字段不能为空"));
            LOG.debug(
                    "IoT ThingModel 多语言字段已写入事务 | productId={} version={}",
                    checkedProductId,
                    version);
            return result;
        });
    }

    /** 切换 Product App Profile；启用前验证至少一个物模型和协议全部可用。 */
    public void changeProductStatus(
            long productId,
            @NotNull IotProfileStatus targetStatus) {
        Product product = products.loadById(productId);
        writeProduct(product.code(), () -> {
            IotProductProfile profile = repository.findProductProfile(productId)
                    .orElseThrow(() -> new BusinessException(IotErrorCode.PRODUCT_NOT_FOUND));
            IotProfileStatus target = Objects.requireNonNull(targetStatus, "Product 目标状态不能为空");
            if (profile.status() == target) {
                return;
            }
            Product current = products.loadById(productId);
            if (target == IotProfileStatus.ENABLED) {
                List<ThingModelPresentation> models = thingModelRepository.findPublished(current.code());
                if (current.protocolCodes().isEmpty() || models.isEmpty()) {
                    throw new BusinessException(IotErrorCode.STATE_CONFLICT);
                }
                try {
                    current.protocolCodes()
                            .forEach(
                                    code -> models.forEach(
                                            model -> protocols.loadByCode(code).validate(model.model())));
                } catch (IllegalArgumentException exception) {
                    throw new BusinessException(IotErrorCode.PROTOCOL_CONFLICT, exception);
                }
            }
            repository.changeProductStatus(productId, target);
            LOG.debug(
                    "IoT Product 状态变更已写入事务 | productId={} status={}",
                    productId,
                    target);
        });
    }

    /**
     * 在十分钟窗口内按 Idempotency-Key 原子创建 Device，并重放同一加密结果和 Secret。
     */
    @CheckReturnValue
    public @NotNull IotDeviceCredentials createDevice(
            @NotNull String idempotencyKey,
            @NotNull DeviceRegistration registration,
            @Nullable X509CredentialRegistration certificate) {
        String key = validateIdempotencyKey(idempotencyKey);
        DeviceRegistration input = Objects.requireNonNull(registration, "Device 创建数据不能为空");
        credentials.validateCredentials(input.accessMethods(), certificate);
        String requestHash = hashRegistration(input, certificate);
        String productCode = productDefinitions.resolve(input.product()).binding().code();
        return writeProductDeviceCreation(productCode, key, () -> {
            // 在 Product 和幂等键锁内认领请求；已完成的同请求返回原结果，不同请求拒绝复用键。
            Instant now = clock.instant();
            boolean claimed = repository.claimDeviceCreation(
                    key,
                    requestHash,
                    now.plus(DEVICE_CREATION_REPLAY_WINDOW),
                    now);
            if (!claimed) {
                DeviceCreationRequestRecord stored = repository.findCompletedDeviceCreation(key, now)
                        .orElseThrow(() -> new BusinessException(IotErrorCode.STATE_CONFLICT));
                if (!MessageDigest.isEqual(
                        stored.requestHash().getBytes(StandardCharsets.US_ASCII),
                        requestHash.getBytes(StandardCharsets.US_ASCII))) {
                    throw new BusinessException(IotErrorCode.STATE_CONFLICT);
                }
                return restoreCreation(stored);
            }

            // 创建 Subject、可选 Account、Device、Credential 和 App Profile，并保持初始状态为停用。
            Subject subject = identities.createSubject(SubjectType.DEVICE);
            Account account = credentials.requiresAccount(input.accessMethods())
                    ? identities.createAccount(subject.subjectId(), DeviceCredentialService.DEVICE_REALM)
                    : null;
            Device device = devices.create(subject.subjectId(), input);
            IotDeviceCredentials created = credentials.issueCredentials(device, account, certificate);
            repository.addDeviceProfile(device.id());
            identities.changeSubjectStatus(device.id(), IdentityStatus.DISABLED);
            if (account != null) {
                identities.changeAccountStatus(account.accountId(), IdentityStatus.DISABLED);
            }

            // 加密保存本次签发结果供窗口内重放，明文缓冲区无论成功或失败都会立即清零。
            DeviceCreationReplay replay = new DeviceCreationReplay(
                    created.device().id(),
                    created.accountId().orElse(null),
                    created.sharedSecret().orElse(null),
                    created.hmacKey().orElse(null));
            byte[] plaintext = encodeReplay(replay);
            try {
                SecretCipher cipher = keyring.activeCipher();
                EncryptedSecret encrypted = cipher.encrypt(creationPurpose(key), plaintext);
                repository.completeDeviceCreation(
                        key,
                        requestHash,
                        encrypted.keyId(),
                        encrypted.ciphertext());
            } finally {
                Arrays.fill(plaintext, (byte) 0);
            }
            LOG.debug(
                    "IoT Device 创建数据已写入事务 | deviceId={} productCode={} productSource={}",
                    created.device().id(),
                    created.device().product().code(),
                    created.device().product().source());
            return created;
        });
    }

    /**
     * 删除一批已经超过重放窗口的 Device 创建幂等记录。
     *
     * @param batchSize 本轮最多删除的记录数，范围为 1 到 10000
     * @return 本轮实际删除数
     */
    @Transactional
    public int cleanupExpiredDeviceCreations(int batchSize) {
        return repository.deleteExpiredDeviceCreations(clock.instant(), batchSize);
    }

    /** 只在 Device App Profile 停用时修改展示资料。 */
    public void updateDeviceDetails(long deviceId, @NotNull DeviceDetailsChange change) {
        writeDevice(deviceId, () -> {
            assertDeviceDisabled(deviceId);
            devices.updateDetails(deviceId, Objects.requireNonNull(change, "Device 展示资料不能为空"));
        });
    }

    /** 只在 Device App Profile 停用时切换物模型版本和协议。 */
    public void rebindDevice(long deviceId, @NotNull DeviceBindingChange change) {
        Device current = devices.loadById(deviceId);
        writeProductDevice(current.product().code(), deviceId, () -> {
            assertDeviceDisabled(deviceId);
            devices.rebindDisabledDevice(deviceId, Objects.requireNonNull(change, "Device 绑定修改不能为空"));
        });
    }

    /** 只在 Device App Profile 停用时完整替换直接连接绑定和接入方式。 */
    @CheckReturnValue
    public @NotNull IotDeviceCredentials changeDeviceConnection(
            long deviceId,
            @Nullable DeviceConnectionBinding connection,
            @NotNull Set<DeviceAccessMethod> accessMethods,
            @Nullable X509CredentialRegistration certificate) {
        Device current = devices.loadById(deviceId);
        return writeProductDevice(current.product().code(), deviceId, () -> {
            assertDeviceDisabled(deviceId);
            Set<DeviceAccessMethod> targetMethods = Set.copyOf(
                    Objects.requireNonNull(accessMethods, "Device 接入方式不能为空"));
            credentials.validateCredentials(targetMethods, certificate);
            Device updated = devices.changeConnection(
                    deviceId,
                    new DeviceConnectionChange(connection, targetMethods));

            Account account = credentials.findAccount(deviceId).orElse(null);
            if (credentials.requiresAccount(targetMethods) && account == null) {
                account = identities.createAccount(
                        deviceId,
                        DeviceCredentialService.DEVICE_REALM,
                        IdentityStatus.DISABLED);
            }
            IotDeviceCredentials changed = credentials.replaceCredentials(
                    updated,
                    account,
                    certificate,
                    clock.instant());
            LOG.debug(
                    "IoT Device 接入方式和凭据已写入事务 | deviceId={} accountId={} methodCount={} sharedSecretIssued={} hmacKeyIssued={}",
                    deviceId,
                    changed.accountId().orElse(null),
                    targetMethods.size(),
                    changed.sharedSecret().isPresent(),
                    changed.hmacKey().isPresent());
            return changed;
        });
    }

    /** 重置 Device Shared Secret，并终止旧 Session 和连接。 */
    @CheckReturnValue
    public @NotNull IssuedSharedSecret resetDeviceSharedSecret(long deviceId) {
        return writeDevice(deviceId, () -> {
            long checkedDeviceId = repository.findDeviceProfile(deviceId)
                    .orElseThrow(() -> new BusinessException(IotErrorCode.DEVICE_NOT_FOUND))
                    .deviceId();
            IssuedSharedSecret issued = credentials.resetSharedSecret(devices.loadById(checkedDeviceId));
            changes.changed(checkedDeviceId, DeviceChangeArea.DEVICE, clock.instant());
            return issued;
        });
    }

    /** 重置 Device MAC Key，并终止旧 Session 和连接。 */
    @CheckReturnValue
    public @NotNull IssuedHmacKey resetDeviceHmacKey(long deviceId) {
        return writeDevice(deviceId, () -> {
            long checkedDeviceId = repository.findDeviceProfile(deviceId)
                    .orElseThrow(() -> new BusinessException(IotErrorCode.DEVICE_NOT_FOUND))
                    .deviceId();
            IssuedHmacKey issued = credentials.resetHmacKey(devices.loadById(checkedDeviceId));
            changes.changed(checkedDeviceId, DeviceChangeArea.DEVICE, clock.instant());
            return issued;
        });
    }

    /** 只允许启用的 Device 原子修改本次明确 set 和 clear 的 desired 属性。 */
    @CheckReturnValue
    public @NotNull DesiredStateResult changeDesired(
            long deviceId,
            @NotNull DesiredStateChange change) {
        return writeDevice(deviceId, () -> {
            assertDeviceEnabled(deviceId);
            return desiredStates.change(
                    deviceId,
                    Objects.requireNonNull(change, "desired 修改不能为空"),
                    iotProperties.getAcknowledgementTimeout());
        });
    }

    /** 只允许启用的 Device 提交物模型中声明的 command。 */
    @CheckReturnValue
    public @NotNull DeviceDownlink submitCommand(
            long deviceId,
            @NotNull String commandCode,
            @NotNull Map<String, ?> inputs) {
        return writeDevice(deviceId, () -> {
            assertDeviceEnabled(deviceId);
            return deviceCommands.submit(
                    deviceId,
                    Objects.requireNonNull(commandCode, "command code 不能为空"),
                    Objects.requireNonNull(inputs, "command 输入不能为空"),
                    null,
                    iotProperties.getAcknowledgementTimeout());
        });
    }

    /** @return 分页公共 Product 及批量读取的 App Profile */
    @Transactional(readOnly = true)
    @CheckReturnValue
    public @NotNull Page<IotProductDetails> findProducts(@NotNull PageRequest page) {
        // 先取得当前 Product 页，再一次读取本页全部 App Profile，避免逐 Product 查询。
        Page<Product> productsPage = products.findPage(page);
        Map<Long, IotProductProfile> profiles = repository.findProductProfiles(
                productsPage.getItems().stream().map(Product::id).toList());
        List<IotProductDetails> items = productsPage.getItems()
                .stream()
                .map(
                        product -> new IotProductDetails(
                                product,
                                Objects.requireNonNull(
                                        profiles.get(product.id()),
                                        "Product App Profile 不存在")))
                .toList();
        return new Page<>(items, page, productsPage.getTotal());
    }

    /** @return 按 code 分页的数据库和代码 Product 管理目录 */
    @Transactional(readOnly = true)
    @CheckReturnValue
    public @NotNull Page<IotProductCatalogItem> findProductCatalog(@NotNull PageRequest page) {
        // 先完成数据库与代码 Product 的统一分页，再收集本页需要 App Profile 的数据库 Product。
        Page<ProductResolution> catalogPage = productDefinitions.findPage(page);
        List<Long> productIds = catalogPage.getItems()
                .stream()
                .filter(resolution -> resolution.binding().source() == ProductSource.DATABASE)
                .map(resolution -> resolution.binding().databaseProductId().orElseThrow())
                .toList();
        Map<Long, IotProductProfile> profiles = repository.findProductProfiles(productIds);

        // 按目录页原顺序组装展示项；代码 Product 没有应用数据库中的 Profile。
        List<IotProductCatalogItem> items = catalogPage.getItems()
                .stream()
                .map(resolution -> {
                    Long productId = resolution.binding().source() == ProductSource.DATABASE
                            ? resolution.binding().databaseProductId().orElseThrow()
                            : null;
                    return new IotProductCatalogItem(
                            resolution.binding(),
                            resolution.definition(),
                            productId == null
                                    ? null
                                    : Objects.requireNonNull(
                                            profiles.get(productId),
                                            "Product App Profile 不存在"));
                })
                .toList();
        return new Page<>(items, page, catalogPage.getTotal());
    }

    /** @return 应用允许数据库 Product 使用的 Device 接入方式 */
    @CheckReturnValue
    public @NotNull List<DeviceAccessMethod> findSupportedProductAccessMethods() {
        return credentials.supportedAccessMethods();
    }

    /** @return 应用已经安装的全部 DeviceProtocol */
    @CheckReturnValue
    public @NotNull List<DeviceProtocol> findInstalledDeviceProtocols() {
        return protocols.findAll();
    }

    /** @return 任意来源 Product 按版本递增的全部物模型 */
    @Transactional(readOnly = true)
    @CheckReturnValue
    public @NotNull List<ThingModelPresentation> findCatalogThingModels(
            @NotNull String productCode) {
        String code = catalogProductCode(productCode);
        return thingModelRepository.findPublished(code);
    }

    /** @return 任意来源 Product 的指定物模型版本 */
    @Transactional(readOnly = true)
    @CheckReturnValue
    public @NotNull ThingModelPresentation catalogThingModelByCodeVersion(
            @NotNull String productCode,
            int version) {
        return thingModels.loadByProductVersion(catalogProductCode(productCode), version);
    }

    /** @return 分页公共 Device 及批量读取的 App Profile */
    @Transactional(readOnly = true)
    @CheckReturnValue
    public @NotNull Page<IotDeviceDetails> findDevices(@NotNull PageRequest page) {
        // 先取得当前 Device 页，再一次读取本页全部 App Profile，避免逐 Device 查询。
        Page<Device> devicesPage = devices.find(
                DeviceQuery.list(
                        new ListRequest(
                                page,
                                List.of(Sort.CREATED_AT.asc(), Sort.SERIAL.asc())))
                        .build());
        Map<Long, IotDeviceProfile> profiles = repository.findDeviceProfiles(
                devicesPage.getItems().stream().map(Device::id).toList());
        List<IotDeviceDetails> items = devicesPage.getItems()
                .stream()
                .map(
                        device -> new IotDeviceDetails(
                                device,
                                Objects.requireNonNull(
                                        profiles.get(device.id()),
                                        "Device App Profile 不存在")))
                .toList();
        return new Page<>(items, page, devicesPage.getTotal());
    }

    /** @return 必须存在的公共 Product 及对应 App Profile */
    @Transactional(readOnly = true)
    @CheckReturnValue
    public @NotNull IotProductDetails productById(long productId) {
        return new IotProductDetails(products.loadById(productId), productProfileById(productId));
    }

    /** 要求 Product code 已由数据库或应用代码登记。 */
    private @NotNull String catalogProductCode(@NotNull String productCode) {
        String code = Objects.requireNonNull(productCode, "Product code 不能为空");
        return productDefinitions.find(code)
                .orElseThrow(() -> new BusinessException(IotErrorCode.PRODUCT_NOT_FOUND))
                .binding()
                .code();
    }

    /** @return 必须存在的公共 Device 及对应 App Profile */
    @Transactional(readOnly = true)
    @CheckReturnValue
    public @NotNull IotDeviceDetails deviceById(long deviceId) {
        Device device = devices.loadById(deviceId);
        return new IotDeviceDetails(device, deviceProfileById(deviceId));
    }

    /**
     * 原子切换 Device、IAM Subject 与可选 Account 状态；停用后登记断连任务并终止 Session。
     */
    public void changeDeviceStatus(
            long deviceId,
            @NotNull IotProfileStatus targetStatus) {
        Device existing = devices.loadById(deviceId);
        writeProductDevice(existing.product().code(), deviceId, () -> {
            IotDeviceProfile profile = repository.findDeviceProfile(deviceId)
                    .orElseThrow(() -> new BusinessException(IotErrorCode.DEVICE_NOT_FOUND));
            IotProfileStatus target = Objects.requireNonNull(targetStatus, "Device 目标状态不能为空");
            if (profile.status() == target) {
                return;
            }
            Device device = devices.loadById(deviceId);
            Account account = credentials.findAccount(deviceId).orElse(null);
            Long accountId = account == null ? null : account.accountId();
            if (target == IotProfileStatus.ENABLED) {
                if (device.product().source() == ProductSource.DATABASE) {
                    long productId = device.product()
                            .databaseProductId()
                            .orElseThrow(() -> new IllegalStateException("数据库 Product 绑定缺少标识"));
                    IotProductProfile productProfile = repository.findProductProfile(productId)
                            .orElseThrow(() -> new BusinessException(IotErrorCode.PRODUCT_NOT_FOUND));
                    if (productProfile.status() != IotProfileStatus.ENABLED) {
                        throw new BusinessException(IotErrorCode.STATE_CONFLICT);
                    }
                } else {
                    ProductDefinition definition = productDefinitions.definition(device.product());
                    LOG.debug(
                            "代码 Product 可用于启用 Device | deviceId={} productCode={}",
                            deviceId,
                            definition.code());
                }
                devices.validateTechnicalActivation(deviceId);
                credentials.validateActivation(device, account);
                identities.changeSubjectStatus(deviceId, IdentityStatus.ACTIVE);
                if (accountId != null && credentials.requiresAccount(device.accessMethods())) {
                    identities.changeAccountStatus(accountId, IdentityStatus.ACTIVE);
                }
            } else {
                identities.changeSubjectStatus(deviceId, IdentityStatus.DISABLED);
                if (accountId != null) {
                    identities.changeAccountStatus(accountId, IdentityStatus.DISABLED);
                }
            }
            repository.changeDeviceStatus(deviceId, target);
            if (target == IotProfileStatus.DISABLED && accountId != null) {
                sessions.terminateAccount(accountId, SessionTerminationReason.IDENTITY_UNAVAILABLE);
            }
            changes.changed(deviceId, DeviceChangeArea.DEVICE, clock.instant());
            LOG.debug(
                    "IoT Device 状态变更已写入事务 | deviceId={} status={}",
                    deviceId,
                    target);
        });
    }

    /** @return 必须存在的 Product App Profile */
    @Transactional(readOnly = true)
    @CheckReturnValue
    public @NotNull IotProductProfile productProfileById(long productId) {
        return repository.findProductProfile(productId)
                .orElseThrow(() -> new BusinessException(IotErrorCode.PRODUCT_NOT_FOUND));
    }

    /** @return 必须存在的 Device App Profile */
    @Transactional(readOnly = true)
    @CheckReturnValue
    public @NotNull IotDeviceProfile deviceProfileById(long deviceId) {
        return repository.findDeviceProfile(deviceId)
                .orElseThrow(() -> new BusinessException(IotErrorCode.DEVICE_NOT_FOUND));
    }

    /** 取得 Product 聚合锁后，在事务内执行有返回值的应用写操作。 */
    private <T> @NotNull T writeProduct(
            @NotNull String productCode,
            @NotNull Supplier<@NotNull T> operation) {
        return distributedLock.execute(
                new LockKey(TreeCode.of("IOT:PRODUCT"), productCode),
                LOCK_WAIT,
                () -> Objects.requireNonNull(
                        transactions.execute(status -> operation.get()),
                        "IoT Product 应用写操作结果不能为空"));
    }

    /** 取得 Product 聚合锁后，在事务内执行无返回值的应用写操作。 */
    private void writeProduct(@NotNull String productCode, @NotNull Runnable operation) {
        distributedLock.execute(
                new LockKey(TreeCode.of("IOT:PRODUCT"), productCode),
                LOCK_WAIT,
                () -> {
                    transactions.executeWithoutResult(status -> operation.run());
                    return null;
                });
    }

    /** 取得 Device 聚合锁后，在事务内执行无返回值的应用写操作。 */
    private void writeDevice(long deviceId, @NotNull Runnable operation) {
        distributedLock.execute(
                new LockKey(TreeCode.of("IOT:DEVICE"), deviceId),
                LOCK_WAIT,
                () -> {
                    transactions.executeWithoutResult(status -> operation.run());
                    return null;
                });
    }

    /** 取得 Device 聚合锁后，在事务内执行有返回值的应用写操作。 */
    private <T> @NotNull T writeDevice(
            long deviceId,
            @NotNull Supplier<@NotNull T> operation) {
        return distributedLock.execute(
                new LockKey(TreeCode.of("IOT:DEVICE"), deviceId),
                LOCK_WAIT,
                () -> Objects.requireNonNull(
                        transactions.execute(status -> operation.get()),
                        "IoT Device 应用写操作结果不能为空"));
    }

    /** 按 Product、Device 的固定顺序取得聚合锁，再在事务内执行有返回值的应用写操作。 */
    private <T> @NotNull T writeProductDevice(
            @NotNull String productCode,
            long deviceId,
            @NotNull Supplier<@NotNull T> operation) {
        return distributedLock.execute(
                new LockKey(TreeCode.of("IOT:PRODUCT"), productCode),
                LOCK_WAIT,
                () -> distributedLock.execute(
                        new LockKey(TreeCode.of("IOT:DEVICE"), deviceId),
                        LOCK_WAIT,
                        () -> Objects.requireNonNull(
                                transactions.execute(status -> operation.get()),
                                "IoT Device 应用写操作结果不能为空")));
    }

    /** 按 Product、Device 的固定顺序取得聚合锁，再在事务内执行无返回值的应用写操作。 */
    private void writeProductDevice(
            @NotNull String productCode,
            long deviceId,
            @NotNull Runnable operation) {
        distributedLock.execute(
                new LockKey(TreeCode.of("IOT:PRODUCT"), productCode),
                LOCK_WAIT,
                () -> distributedLock.execute(
                        new LockKey(TreeCode.of("IOT:DEVICE"), deviceId),
                        LOCK_WAIT,
                        () -> {
                            transactions.executeWithoutResult(status -> operation.run());
                            return null;
                        }));
    }

    /** 按 Product、幂等键顺序取得创建锁，再在事务内创建 Device 及关联身份数据。 */
    private <T> @NotNull T writeProductDeviceCreation(
            @NotNull String productCode,
            @NotNull String idempotencyKey,
            @NotNull Supplier<@NotNull T> operation) {
        return distributedLock.execute(
                new LockKey(TreeCode.of("IOT:PRODUCT"), productCode),
                LOCK_WAIT,
                () -> distributedLock.execute(
                        new LockKey(TreeCode.of("APP:IOT:DEVICE_CREATE"), DigestUtil.sha256Hex(idempotencyKey)),
                        LOCK_WAIT,
                        () -> Objects.requireNonNull(
                                transactions.execute(status -> operation.get()),
                                "IoT Device 创建结果不能为空")));
    }

    /** 要求 Product App Profile 当前为停用状态。 */
    private void assertProductDisabled(long productId) {
        IotProductProfile profile = repository.findProductProfile(productId)
                .orElseThrow(() -> new BusinessException(IotErrorCode.PRODUCT_NOT_FOUND));
        if (profile.status() != IotProfileStatus.DISABLED) {
            throw new BusinessException(IotErrorCode.STATE_CONFLICT);
        }
    }

    /** 要求 Device App Profile 当前为停用状态。 */
    private void assertDeviceDisabled(long deviceId) {
        IotDeviceProfile profile = repository.findDeviceProfile(deviceId)
                .orElseThrow(() -> new BusinessException(IotErrorCode.DEVICE_NOT_FOUND));
        if (profile.status() != IotProfileStatus.DISABLED) {
            throw new BusinessException(IotErrorCode.STATE_CONFLICT);
        }
    }

    /** 要求 Device App Profile 当前为启用状态。 */
    private void assertDeviceEnabled(long deviceId) {
        IotDeviceProfile profile = repository.findDeviceProfile(deviceId)
                .orElseThrow(() -> new BusinessException(IotErrorCode.DEVICE_NOT_FOUND));
        if (profile.status() != IotProfileStatus.ENABLED) {
            throw new BusinessException(IotErrorCode.STATE_CONFLICT);
        }
    }

    /** 校验 HTTP Idempotency-Key 可以稳定保存到主库。 */
    private static String validateIdempotencyKey(String idempotencyKey) {
        String checked = Objects.requireNonNull(idempotencyKey, "Idempotency-Key 不能为空").trim();
        if (checked.isEmpty() || checked.length() > 128) {
            throw new BusinessException(IotErrorCode.INVALID_INPUT);
        }
        return checked;
    }

    /** 把 Device 创建输入编码为字段顺序固定的 JSON 后计算请求摘要。 */
    private String hashRegistration(
            DeviceRegistration registration,
            @Nullable X509CredentialRegistration certificate) {
        ProductSelector product = registration.product();
        return DigestUtil.sha256Hex(
                DataOps.obj()
                        .put("productSource", product.source().name())
                        .put("productId", product.databaseProductId())
                        .put("productCode", product.codeProductCode())
                        .put("code", registration.code())
                        .put("serialNumber", registration.serialNumber())
                        .put("displayName", registration.displayName())
                        .put("remark", registration.remark())
                        .put("thingModelVersion", registration.thingModelVersion())
                        .put("protocolCode", registration.protocolCode())
                        .put(
                                "connection",
                                registration.connection()
                                        .map(
                                                value -> Map.of(
                                                        "protocol",
                                                        value.definition().protocol().code(),
                                                        "identityType",
                                                        value.definition().identifierType(),
                                                        "identity",
                                                        value.identifierValue()))
                                        .orElse(null))
                        .put(
                                "accessMethods",
                                registration.accessMethods()
                                        .stream()
                                        .map(method -> method.code().format(COLON))
                                        .sorted()
                                        .toList())
                        .put(
                                "certificateSha256",
                                certificate == null ? null : DigestUtil.sha256Hex(certificate.certificateDer()))
                        .bytes());
    }

    /** 编码只存入加密列的 Device 创建重放结果。 */
    private byte[] encodeReplay(DeviceCreationReplay replay) {
        return DataOps.obj()
                .put("formatVersion", 2)
                .put("deviceId", replay.deviceId())
                .put("accountId", replay.accountId())
                .put("sharedSecret", replay.sharedSecret())
                .put("hmacKey", replay.hmacKey())
                .bytes();
    }

    /** 解密幂等结果并重新装载公共 Device。 */
    private IotDeviceCredentials restoreCreation(DeviceCreationRequestRecord record) {
        SecretCipher cipher = keyring.findCipher(record.responseKeyId())
                .orElseThrow(() -> new IllegalStateException("Device 创建幂等结果使用的密钥不存在"));
        byte[] plaintext = cipher.decrypt(
                creationPurpose(record.idempotencyKey()),
                new EncryptedSecret(record.responseKeyId(), record.responseCiphertext()));
        try {
            JsonNode root = objectMapper.readTree(plaintext);
            if (root.path("formatVersion").asInt() != 2 || !root.path("deviceId").canConvertToLong()) {
                throw new IllegalStateException("Device 创建幂等结果格式无效");
            }
            long deviceId = root.path("deviceId").longValue();
            Long accountId = root.path("accountId").isNull() ? null : root.path("accountId").longValue();
            String sharedSecret = root.path("sharedSecret").isNull()
                    ? null
                    : root.path("sharedSecret").textValue();
            String hmacKey = root.path("hmacKey").isNull() ? null : root.path("hmacKey").textValue();
            return new IotDeviceCredentials(devices.loadById(deviceId), accountId, sharedSecret, hmacKey);
        } catch (IOException exception) {
            throw new IllegalStateException("Device 创建幂等结果无法解码", exception);
        } finally {
            Arrays.fill(plaintext, (byte) 0);
        }
    }

    /** 返回加密幂等结果使用且不包含原始 Idempotency-Key 的 purpose。 */
    private static String creationPurpose(String idempotencyKey) {
        return DEVICE_CREATION_PURPOSE_PREFIX
                + DigestUtil.sha256Hex(idempotencyKey);
    }
}
