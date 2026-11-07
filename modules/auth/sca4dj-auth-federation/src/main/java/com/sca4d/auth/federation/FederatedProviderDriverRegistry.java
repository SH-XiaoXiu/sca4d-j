package com.sca4d.auth.federation;

import com.sca4d.core.api.error.exception.BusinessException;
import com.sca4d.core.api.name.NameFormat;
import com.sca4d.core.api.name.code.TreeCode;
import org.jetbrains.annotations.NotNull;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/** 保存 Spring 容器中全部 {@link FederatedProviderDriver}，按编码查找；启动时拒绝重复和非法编码。 */
public final class FederatedProviderDriverRegistry {

    /** 与 {@code auth_federated_provider_configuration.driver_code} 列宽一致。 */
    private static final int MAXIMUM_CODE_LENGTH = 64;

    private final @NotNull Map<TreeCode, FederatedProviderDriver> drivers;

    /**
     * 登记 Driver。
     *
     * @param candidates 容器中的全部 Driver，可以为空
     * @throws IllegalStateException 编码含通配节点、超过列宽或重复时抛出
     */
    public FederatedProviderDriverRegistry(@NotNull List<FederatedProviderDriver> candidates) {
        Map<TreeCode, FederatedProviderDriver> values = new HashMap<>();
        for (FederatedProviderDriver candidate : Objects.requireNonNull(candidates, "Driver 列表不能为空")) {
            TreeCode code = Objects.requireNonNull(candidate, "Driver 不能为空").code();
            String text = code.format(NameFormat.COLON);
            if (code.nodes().contains("*") || text.length() > MAXIMUM_CODE_LENGTH) {
                throw new IllegalStateException("联邦 Driver 编码不能含通配节点且不能超过 64 个字符: " + text);
            }
            if (values.putIfAbsent(code, candidate) != null) {
                throw new IllegalStateException("联邦 Driver 编码重复: " + text);
            }
        }
        this.drivers = Map.copyOf(values);
    }

    /** @return 编码已登记时为 true */
    public boolean contains(@NotNull TreeCode code) {
        return drivers.containsKey(Objects.requireNonNull(code, "Driver 编码不能为空"));
    }

    /**
     * 按编码读取 Driver。
     *
     * @param code Provider 配置选择的 Driver 编码
     * @return 已登记的 Driver
     * @throws BusinessException 编码没有登记时抛出 {@link FederatedErrorCode#CONFIGURATION_INVALID}
     */
    public @NotNull FederatedProviderDriver require(@NotNull TreeCode code) {
        FederatedProviderDriver driver = drivers.get(Objects.requireNonNull(code, "Driver 编码不能为空"));
        if (driver == null) {
            throw new BusinessException(FederatedErrorCode.CONFIGURATION_INVALID);
        }
        return driver;
    }
}
