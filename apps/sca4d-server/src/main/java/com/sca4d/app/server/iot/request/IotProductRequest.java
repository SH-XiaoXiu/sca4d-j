package com.sca4d.app.server.iot.request;

import com.sca4d.core.api.i18n.LocalizedFields;
import com.sca4d.core.spring.validation.Req;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Objects;
import java.util.Set;

/** 接收 Product 创建或完整技术定义修改所需的字段。 */
@SuppressWarnings("unused")
public final class IotProductRequest {

    @Size(max = 128)
    private @Nullable String code;

    @Req.NotNull
    private @Nullable LocalizedFields i18n;

    @Req.NotNull
    @Size(max = 16)
    private @Nullable Set<@NotBlank @Size(max = 128) String> accessMethods;

    @NotEmpty
    @Size(max = 16)
    private @Nullable Set<@NotBlank @Size(max = 128) String> protocolCodes;

    /** 创建供 Jackson 绑定的空请求。 */
    public IotProductRequest() {
    }

    /** @return 创建时使用的 Product code */
    public @NotNull String getCode() {
        return Objects.requireNonNull(code, "Product code 不能为空");
    }

    /** @param code 创建后不可修改的 Product code */
    public void setCode(@Nullable String code) {
        this.code = code;
    }

    /** @return Product 按 Locale 保存的完整展示字段 */
    public @NotNull LocalizedFields getI18n() {
        return Objects.requireNonNull(i18n, "Product 多语言字段不能为空");
    }

    /** @param i18n Product 按 Locale 保存的完整展示字段 */
    public void setI18n(@Nullable LocalizedFields i18n) {
        this.i18n = i18n;
    }

    /** @return 完整允许接入方式 code 集合 */
    public @NotNull Set<String> getAccessMethods() {
        return Set.copyOf(Objects.requireNonNull(accessMethods, "Product 接入方式不能为空"));
    }

    /** @param accessMethods 完整允许接入方式 code 集合 */
    public void setAccessMethods(@Nullable Set<String> accessMethods) {
        this.accessMethods = accessMethods == null ? null : Set.copyOf(accessMethods);
    }

    /** @return 完整兼容协议 code 集合 */
    public @NotNull Set<String> getProtocolCodes() {
        return Set.copyOf(Objects.requireNonNull(protocolCodes, "Product 协议不能为空"));
    }

    /** @param protocolCodes 完整兼容协议 code 集合 */
    public void setProtocolCodes(@Nullable Set<String> protocolCodes) {
        this.protocolCodes = protocolCodes == null ? null : Set.copyOf(protocolCodes);
    }
}
