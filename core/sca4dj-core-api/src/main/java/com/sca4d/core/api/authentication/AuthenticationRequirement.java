package com.sca4d.core.api.authentication;

import com.sca4d.core.api.utils.DurationUtils;
import org.jetbrains.annotations.Nullable;

import java.time.Duration;
import java.util.Optional;

/**
 * 表示完成一个动作所需的独立认证因素数量，以及是否允许复用已有认证。
 */
public class AuthenticationRequirement {

    private final int factorCount;

    private final Duration reusableAuthenticationMaximumAge;

    /**
     * 创建动作认证要求。
     *
     * @param factorCount                      需要的不同 {@link AuthenticationFactorKind} 数量，范围为 0 到 3
     * @param reusableAuthenticationMaximumAge 允许复用已有认证时的最大年龄；不允许复用时为 {@code null}
     * @throws IllegalArgumentException 因素数量超出范围，或最大年龄不是正时长时抛出
     */
    public AuthenticationRequirement(
            int factorCount,
            @Nullable Duration reusableAuthenticationMaximumAge) {
        if (factorCount < 0 || factorCount > AuthenticationFactorKind.values().length) {
            throw new IllegalArgumentException("认证因素数量必须在 0 到 3 之间");
        }
        this.factorCount = factorCount;
        this.reusableAuthenticationMaximumAge = reusableAuthenticationMaximumAge == null
                ? null
                : DurationUtils.requirePositive(reusableAuthenticationMaximumAge, "可复用认证最大年龄");
    }

    /**
     * 返回完成动作所需的不同认证因素数量。
     *
     * @return 0 到 3；0 表示动作本身不要求额外认证
     */
    public int factorCount() {
        return factorCount;
    }

    /**
     * 返回允许从已有认证记录中复用因素的最大年龄。
     *
     * @return 允许复用时包含正时长；为空时本次动作只接受当前尝试新完成的认证
     */
    public Optional<Duration> reusableAuthenticationMaximumAge() {
        return Optional.ofNullable(reusableAuthenticationMaximumAge);
    }
}
