package com.sca4d.iam.config;

import com.sca4d.core.api.identity.SystemPrincipal;
import com.sca4d.core.api.identity.SystemPrincipals;
import com.sca4d.core.api.name.code.TreeCode;
import com.sca4d.core.api.runtime.InstanceCapability;
import com.sca4d.iam.identity.SystemIdentityRepository;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.SmartInitializingSingleton;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/** 在全部单例和数据库迁移完成后、SmartLifecycle 启动前发布系统身份。 */
public final class SystemIdentityInitializer implements SmartInitializingSingleton {

    private static final Logger LOG = LoggerFactory.getLogger(SystemIdentityInitializer.class);

    private final @NotNull SystemIdentityRepository repository;

    private final @NotNull TransactionTemplate transaction;

    private final @NotNull SystemIdentityProperties properties;

    private final @NotNull List<InstanceCapability> capabilities;

    public SystemIdentityInitializer(
            @NotNull SystemIdentityRepository repository,
            @NotNull TransactionTemplate transaction,
            @NotNull SystemIdentityProperties properties,
            @NotNull List<InstanceCapability> capabilities) {
        this.repository = Objects.requireNonNull(repository);
        this.transaction = Objects.requireNonNull(transaction);
        this.properties = Objects.requireNonNull(properties);
        this.capabilities = List.copyOf(capabilities);
    }

    @Override
    public void afterSingletonsInstantiated() {
        Map<TreeCode, Long> configured = new LinkedHashMap<>();
        configured.put(SystemPrincipals.MAIN, 1L);
        properties.getSystemPrincipalIds().forEach((text, id) -> {
            TreeCode code = TreeCode.of(text);
            if (code.hasNode(TreeCode.ofSegment("*")) || id == null || id <= 0) {
                throw new IllegalStateException("系统身份必须使用固定编码和正数 Principal ID: " + text);
            }
            configured.put(code, id);
        });
        if (new HashSet<>(configured.values()).size() != configured.size()) {
            throw new IllegalStateException("不同系统身份不能配置相同 Principal ID，请配置其他 ID");
        }
        for (InstanceCapability capability : capabilities) {
            if (!configured.containsKey(capability.systemIdentity())) {
                throw new IllegalStateException(
                        "系统身份必须配置 sca4dj.iam.system-principal-ids." + capability.systemIdentity());
            }
        }
        // execute 返回意味着提交已经成功，失败时不发布任何运行期常量。
        Map<TreeCode, SystemPrincipal> confirmed = Objects.requireNonNull(
                transaction.execute(status -> repository.register(configured)));
        SystemPrincipals.publish(confirmed);
        LOG.info("系统身份已完成数据库确认并发布 | principalCount={}", confirmed.size());
    }
}
