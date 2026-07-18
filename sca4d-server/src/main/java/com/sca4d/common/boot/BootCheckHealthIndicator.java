package com.sca4d.common.boot;

import lombok.RequiredArgsConstructor;
import org.jetbrains.annotations.NotNull;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

/** 将启动检查结果汇总到 Actuator 健康端点。 */
@Component("bootChecksHealthIndicator")
@RequiredArgsConstructor
public class BootCheckHealthIndicator implements HealthIndicator {

    private final BootUtils bootUtils;
    private final List<BootCheck> checks;

    @Override
    public @NotNull Health health() {
        Map<String, BootCheckResult> results = bootUtils.healthResults();
        Health.Builder builder = Health.up();
        for (BootCheck check : checks) {
            BootCheckResult result = results.get(check.name());
            if (result == null) {
                continue;
            }
            builder.withDetail(check.name(), result.getMessage());
            if (!result.isAvailable() && check.policy().getCriticality() != DependencyCriticality.OPTIONAL) {
                builder.down();
            }
        }
        return builder.build();
    }
}
