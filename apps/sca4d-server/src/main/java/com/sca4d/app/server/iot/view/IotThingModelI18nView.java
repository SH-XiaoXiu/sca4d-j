package com.sca4d.app.server.iot.view;

import com.sca4d.core.api.i18n.LocalizedFields;
import com.sca4d.iot.api.model.ThingModelPresentation;
import org.jetbrains.annotations.CheckReturnValue;
import org.jetbrains.annotations.NotNull;

import java.util.Objects;

/** 返回一个已发布 ThingModel 版本可独立编辑的完整多语言字段。 */
@SuppressWarnings("unused")
public class IotThingModelI18nView {

    private final @NotNull LocalizedFields i18n;

    private final @NotNull String productCode;

    private final int version;

    /** 从数据库 ThingModel 展示结果创建完整多语言字段视图。 */
    private IotThingModelI18nView(@NotNull ThingModelPresentation presentation) {
        this.productCode = presentation.model().productCode();
        this.version = presentation.model().version();
        this.i18n = presentation.i18n()
                .orElseThrow(() -> new IllegalArgumentException("代码 ThingModel 不包含动态多语言字段"));
    }

    /** @param presentation 当前 ThingModel 展示结果 @return 完整多语言字段视图 */
    @CheckReturnValue
    public static @NotNull IotThingModelI18nView from(
            @NotNull ThingModelPresentation presentation) {
        return new IotThingModelI18nView(
                Objects.requireNonNull(presentation, "ThingModel 展示结果不能为空"));
    }

    /** @return 所属 Product code */
    public @NotNull String getProductCode() {
        return productCode;
    }

    /** @return ThingModel 版本 */
    public int getVersion() {
        return version;
    }

    /** @return 当前版本保存的全部 Locale 和展示路径 */
    public @NotNull LocalizedFields getI18n() {
        return i18n;
    }
}
