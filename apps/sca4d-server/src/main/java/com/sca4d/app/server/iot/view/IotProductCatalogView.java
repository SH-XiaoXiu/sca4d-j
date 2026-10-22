package com.sca4d.app.server.iot.view;

import static com.sca4d.core.api.name.NameFormat.COLON;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.sca4d.app.server.iot.IotProductCatalogItem;
import com.sca4d.app.server.iot.IotProductProfile;
import com.sca4d.app.server.iot.IotProfileStatus;
import com.sca4d.core.api.i18n.LocalizableText;
import com.sca4d.core.api.query.Page;
import com.sca4d.core.spring.web.view.BaseView;
import com.sca4d.core.spring.web.view.Localize;
import com.sca4d.iot.api.DeviceAccessMethod;
import com.sca4d.iot.api.ProductBinding;
import com.sca4d.iot.api.ProductDefinition;
import com.sca4d.iot.api.ProductSource;
import com.sca4d.iot.api.protocol.DeviceProtocol;
import org.jetbrains.annotations.CheckReturnValue;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.Unmodifiable;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Objects;

/** 返回双来源 Product 目录和创建表单允许使用的技术选项。 */
@SuppressWarnings("unused")
public class IotProductCatalogView {

    private final @NotNull List<String> accessMethods;

    private final @NotNull List<ProductItem> products;

    private final @NotNull List<String> protocolCodes;

    private final int page;

    private final int size;

    private final long total;

    /** 从应用目录项和已安装能力创建稳定排序的管理响应。 */
    private IotProductCatalogView(
            @NotNull Page<IotProductCatalogItem> products,
            @NotNull Collection<DeviceAccessMethod> accessMethods,
            @NotNull Collection<DeviceProtocol> protocols) {
        this.products = products.getItems().stream().map(ProductItem::from).toList();
        this.accessMethods = accessMethods.stream().map(method -> method.code().format(COLON)).sorted().toList();
        this.protocolCodes = protocols.stream().map(DeviceProtocol::code).sorted().toList();
        this.page = products.getPage();
        this.size = products.getSize();
        this.total = products.getTotal();
    }

    /** @return 包含双来源 Product 和已安装技术选项的管理目录 */
    @CheckReturnValue
    public static @NotNull IotProductCatalogView from(
            @NotNull Page<IotProductCatalogItem> products,
            @NotNull Collection<DeviceAccessMethod> accessMethods,
            @NotNull Collection<DeviceProtocol> protocols) {
        return new IotProductCatalogView(
                Objects.requireNonNull(products, "Product 目录不能为空"),
                List.copyOf(Objects.requireNonNull(accessMethods, "Device 接入方式不能为空")),
                List.copyOf(Objects.requireNonNull(protocols, "Device 协议不能为空")));
    }

    /** @return 按 Product code 排列的目录项 */
    public @NotNull @Unmodifiable List<ProductItem> getProducts() {
        return List.copyOf(products);
    }

    /** @return 应用允许数据库 Product 选择的接入方式 code */
    public @NotNull @Unmodifiable List<String> getAccessMethods() {
        return List.copyOf(accessMethods);
    }

    /** @return 应用已经安装的 DeviceProtocol code */
    public @NotNull @Unmodifiable List<String> getProtocolCodes() {
        return List.copyOf(protocolCodes);
    }

    /** @return 从一开始的当前页码 */
    public int getPage() {
        return page;
    }

    /** @return 当前请求页大小 */
    public int getSize() {
        return size;
    }

    /** @return 双来源 Product 总数 */
    public long getTotal() {
        return total;
    }

    /** 返回一个数据库或代码 Product 的本地化管理投影。 */
    public static class ProductItem extends BaseView {

        @JsonInclude(JsonInclude.Include.ALWAYS)
        private final @Nullable Long id;

        private final @NotNull ProductSource source;

        private final @NotNull String code;

        @Localize(source = "displayNameText")
        private final @NotNull String displayName;

        private final @NotNull LocalizableText localizedDisplayName;

        private final @NotNull List<String> accessMethods;

        private final @NotNull List<String> protocolCodes;

        @JsonInclude(JsonInclude.Include.ALWAYS)
        private final @Nullable IotProfileStatus status;

        @JsonInclude(JsonInclude.Include.ALWAYS)
        private final @Nullable Instant createdAt;

        @JsonInclude(JsonInclude.Include.ALWAYS)
        private final @Nullable Instant updatedAt;

        /** 从来源一致的目录项创建本地化 HTTP 投影。 */
        private ProductItem(@NotNull IotProductCatalogItem item) {
            ProductBinding binding = item.binding();
            ProductDefinition definition = item.definition();
            IotProductProfile profile = item.profile();
            this.id = binding.databaseProductId().isPresent()
                    ? binding.databaseProductId().getAsLong()
                    : null;
            this.source = binding.source();
            this.code = binding.code();
            this.displayName = definition.code();
            this.localizedDisplayName = definition.displayName();
            this.accessMethods = definition.accessMethods()
                    .stream()
                    .map(method -> method.code().format(COLON))
                    .sorted()
                    .toList();
            this.protocolCodes = definition.protocolCodes().stream().sorted().toList();
            this.status = profile == null ? null : profile.status();
            this.createdAt = profile == null ? null : profile.createdAt();
            this.updatedAt = profile == null ? null : profile.updatedAt();
        }

        /** @return 目录项对应的 HTTP 视图 */
        @CheckReturnValue
        public static @NotNull ProductItem from(@NotNull IotProductCatalogItem item) {
            return new ProductItem(Objects.requireNonNull(item, "Product 目录项不能为空"));
        }

        /** @return 数据库 Product 标识；代码 Product 时为空 */
        public @Nullable Long getId() {
            return id;
        }

        /** @return Product 定义来源 */
        public @NotNull ProductSource getSource() {
            return source;
        }

        /** @return Product 稳定 code */
        public @NotNull String getCode() {
            return code;
        }

        /** @return 当前请求 Locale 的 Product 名称 */
        public @NotNull String getDisplayName() {
            return displayName;
        }

        /** @return 允许的 Device 接入方式 code */
        public @NotNull @Unmodifiable List<String> getAccessMethods() {
            return List.copyOf(accessMethods);
        }

        /** @return 兼容的 DeviceProtocol code */
        public @NotNull @Unmodifiable List<String> getProtocolCodes() {
            return List.copyOf(protocolCodes);
        }

        /** @return 数据库 Product App 状态；代码 Product 时为空 */
        public @Nullable IotProfileStatus getStatus() {
            return status;
        }

        /** @return 数据库 Product Profile 创建时间；代码 Product 时为空 */
        public @Nullable Instant getCreatedAt() {
            return createdAt;
        }

        /** @return 数据库 Product Profile 修改时间；代码 Product 时为空 */
        public @Nullable Instant getUpdatedAt() {
            return updatedAt;
        }

        /** @return Product 展示名称的本地化来源 */
        @SuppressWarnings("ProtectedMemberInFinalClass")
        protected @NotNull LocalizableText displayNameText() {
            return localizedDisplayName;
        }
    }
}
