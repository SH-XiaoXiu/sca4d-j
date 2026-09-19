package com.sca4d.app.server.iot.view;

import com.sca4d.app.server.iot.IotProductDetails;
import com.sca4d.app.server.iot.IotProductProfile;
import com.sca4d.app.server.iot.IotProfileStatus;
import com.sca4d.core.spring.web.view.BaseView;
import com.sca4d.core.spring.web.view.Localize;
import com.sca4d.iot.api.Product;
import org.jetbrains.annotations.CheckReturnValue;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Unmodifiable;

import java.time.Instant;
import java.util.List;
import java.util.Objects;

/** 返回 Sca4d Server 管理 Product 所需的公共定义和 App 启停状态。 */
@SuppressWarnings("unused")
public final class IotProductView extends BaseView {

    private final long id;

    private final @NotNull String code;

    @Localize(key = "displayName")
    private final @NotNull String displayName;

    private final @NotNull @Unmodifiable List<String> accessMethods;

    private final @NotNull @Unmodifiable List<String> protocolCodes;

    private final @NotNull IotProfileStatus status;

    private final @NotNull Instant createdAt;

    private final @NotNull Instant updatedAt;

    /** 从公共 Product 和应用 Profile 创建 HTTP 视图。 */
    private IotProductView(@NotNull Product product, @NotNull IotProductProfile profile) {
        super(product.i18n());
        this.id = product.id();
        this.code = product.code();
        this.displayName = product.code();
        this.accessMethods = product.accessMethods()
                .stream()
                .map(com.sca4d.iot.api.DeviceAccessMethod::code)
                .sorted()
                .toList();
        this.protocolCodes = product.protocolCodes().stream().sorted().toList();
        this.status = profile.status();
        this.createdAt = profile.createdAt();
        this.updatedAt = profile.updatedAt();
    }

    /** @return 合并公共 Product 与 App Profile 后的管理视图 */
    @CheckReturnValue
    public static @NotNull IotProductView from(
            @NotNull Product product,
            @NotNull IotProductProfile profile) {
        Product checkedProduct = Objects.requireNonNull(product, "Product 不能为空");
        IotProductProfile checkedProfile = Objects.requireNonNull(profile, "Product App Profile 不能为空");
        if (checkedProduct.id() != checkedProfile.productId()) {
            throw new IllegalArgumentException("Product 与 App Profile 标识不一致");
        }
        return new IotProductView(checkedProduct, checkedProfile);
    }

    /** @return 应用 Product 查询结果对应的 HTTP 视图 */
    @CheckReturnValue
    public static @NotNull IotProductView from(@NotNull IotProductDetails details) {
        IotProductDetails checked = Objects.requireNonNull(details, "Product 应用查询结果不能为空");
        return from(checked.product(), checked.profile());
    }

    /** @return Product 标识 */
    public long getId() {
        return id;
    }

    /** @return Product 稳定 code */
    public @NotNull String getCode() {
        return code;
    }

    /** @return Product 展示名称 */
    public @NotNull String getDisplayName() {
        return displayName;
    }

    /** @return 允许接入方式 code */
    public @NotNull @Unmodifiable List<String> getAccessMethods() {
        return List.copyOf(accessMethods);
    }

    /** @return 兼容协议 code */
    public @NotNull @Unmodifiable List<String> getProtocolCodes() {
        return List.copyOf(protocolCodes);
    }

    /** @return Sca4d Server 启停状态 */
    public @NotNull IotProfileStatus getStatus() {
        return status;
    }

    /** @return App Profile 创建时间 */
    public @NotNull Instant getCreatedAt() {
        return createdAt;
    }

    /** @return App Profile 最近修改时间 */
    public @NotNull Instant getUpdatedAt() {
        return updatedAt;
    }
}
