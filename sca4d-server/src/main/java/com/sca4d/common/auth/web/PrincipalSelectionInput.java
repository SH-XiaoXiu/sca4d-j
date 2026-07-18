package com.sca4d.common.auth.web;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Web 场景下解析出的 Principal 选择输入。
 *
 * <p>该对象只在 Web 适配层内部使用，不进入 {@code PrincipalSelection} 接口。
 */
public class PrincipalSelectionInput {

    private final Long principalId;
    private final Long tenantId;
    private final String selectionKey;
    private final Map<String, Object> parameters;

    /**
     * 创建 Principal 选择输入。
     *
     * @param principalId Principal ID
     * @param tenantId Tenant ID
     * @param selectionKey 业务选择键
     * @param parameters 额外命名参数
     */
    public PrincipalSelectionInput(
            @Nullable Long principalId,
            @Nullable Long tenantId,
            @Nullable String selectionKey,
            @NotNull Map<String, Object> parameters) {
        this.principalId = principalId;
        this.tenantId = tenantId;
        this.selectionKey = selectionKey;
        this.parameters = Collections.unmodifiableMap(new LinkedHashMap<>(parameters));
    }

    public @Nullable Long getPrincipalId() {
        return principalId;
    }

    public @Nullable Long getTenantId() {
        return tenantId;
    }

    public @Nullable String getSelectionKey() {
        return selectionKey;
    }

    public @NotNull Map<String, Object> getParameters() {
        return parameters;
    }
}
