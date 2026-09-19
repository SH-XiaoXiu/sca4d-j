package com.sca4d.app.server.platform;

import com.sca4d.app.server.service.PlatformAccountApplicationService;
import org.jetbrains.annotations.NotNull;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.util.Objects;

/** 在 IAM 和 Profile 初始化后复核已有资料对应的 `EMAIL` 登录标识。 */
@Component
@Order(300)
public final class PlatformEmailIdentifierBootstrap implements ApplicationRunner {

    private final @NotNull PlatformAccountApplicationService accounts;

    /** @param accounts Platform Profile 与 IAM Email 标识事务入口 */
    public PlatformEmailIdentifierBootstrap(@NotNull PlatformAccountApplicationService accounts) {
        this.accounts = Objects.requireNonNull(accounts, "Platform Account 应用服务不能为空");
    }

    /**
     * 复核全部非空 Profile 邮箱；规范值冲突或身份关系异常时阻止应用带着不一致数据启动。
     *
     * @param arguments 应用启动参数
     */
    @Override
    public void run(@NotNull ApplicationArguments arguments) {
        accounts.synchronizeProfileEmailIdentifiers();
    }
}
