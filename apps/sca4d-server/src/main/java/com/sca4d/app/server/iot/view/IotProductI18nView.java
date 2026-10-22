package com.sca4d.app.server.iot.view;

import com.sca4d.core.api.i18n.LocalizedFields;
import com.sca4d.iot.api.Product;
import org.jetbrains.annotations.CheckReturnValue;
import org.jetbrains.annotations.NotNull;

import java.util.Objects;

/** 返回 Product 编辑界面需要读取的完整多语言字段。 */
@SuppressWarnings("unused")
public class IotProductI18nView {

    private final @NotNull LocalizedFields i18n;

    private final long productId;

    /** 从 Product 创建完整多语言字段视图。 */
    private IotProductI18nView(@NotNull Product product) {
        this.productId = product.id();
        this.i18n = product.i18n();
    }

    /** @param product 当前 Product @return 完整多语言字段视图 */
    @CheckReturnValue
    public static @NotNull IotProductI18nView from(@NotNull Product product) {
        return new IotProductI18nView(Objects.requireNonNull(product, "Product 不能为空"));
    }

    /** @return Product 标识 */
    public long getProductId() {
        return productId;
    }

    /** @return Product 保存的全部 Locale 和展示字段 */
    public @NotNull LocalizedFields getI18n() {
        return i18n;
    }
}
