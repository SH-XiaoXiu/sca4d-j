package com.sca4d.iam.config;

import com.sca4d.core.api.concurrent.DistributedLock;
import com.sca4d.core.api.concurrent.LockKey;
import com.sca4d.core.api.identity.Account;
import com.sca4d.iam.base.rbac.PlatformBootstrapService;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;

import java.time.Duration;
import java.util.Objects;

/** 在应用启动时确保本地平台账号及其身份信息存在。 */
public final class IamBootstrap implements ApplicationRunner {

    private static final Logger LOG = LoggerFactory.getLogger(IamBootstrap.class);

    private static final LockKey BOOTSTRAP_LOCK = new LockKey("iam", "bootstrap");

    private static final Duration BOOTSTRAP_WAIT = Duration.ofSeconds(30);

    private final @NotNull PlatformBootstrapService bootstrap;

    private final @NotNull IamProperties properties;

    private final @NotNull DistributedLock distributedLock;

    /**
     * 创建 IAM 初始化任务。
     *
     * @param bootstrap Platform 初始化服务
     * @param properties 初始化配置
     * @param distributedLock 多实例启动协调锁
     */
    public IamBootstrap(
            @NotNull PlatformBootstrapService bootstrap,
            @NotNull IamProperties properties,
            @NotNull DistributedLock distributedLock) {
        this.bootstrap = Objects.requireNonNull(bootstrap, "Platform 初始化服务不能为空");
        this.properties = Objects.requireNonNull(properties, "IAM 初始化配置不能为空");
        this.distributedLock = Objects.requireNonNull(distributedLock, "分布式锁不能为空");
    }

    /** {@inheritDoc} */
    @Override
    public void run(@NotNull ApplicationArguments arguments) {
        Account account = distributedLock.execute(
                BOOTSTRAP_LOCK,
                BOOTSTRAP_WAIT,
                () -> bootstrap.initialize(properties.getUsername()));
        LOG.info("本地平台账号初始化完成 | accountId={}", account.accountId());
    }
}
