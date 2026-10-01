package com.sca4d.iot.persistence;

import com.sca4d.core.api.i18n.LocalizedFields;
import org.jetbrains.annotations.NotNull;

/** 保存 Mapper 查询得到的 Product 标量字段。 */
@SuppressWarnings("ClassCanBeRecord") // 项目统一使用普通 class，不使用 Java record。
public class ProductState {
    private final @NotNull LocalizedFields i18n;

    private final @NotNull String code;

    private final long id;

    /** @param id Product 标识 @param code Product code @param i18n 完整多语言展示字段 */
    public ProductState(long id, @NotNull String code, @NotNull LocalizedFields i18n) {
        this.id = id;
        this.code = code;
        this.i18n = i18n;
    }

    /** @return Product 标识 */
    public long id() {
        return id;
    }

    /** @return Product code */
    public @NotNull String code() {
        return code;
    }

    /** @return 完整多语言展示字段 */
    public @NotNull LocalizedFields i18n() {
        return i18n;
    }
}
