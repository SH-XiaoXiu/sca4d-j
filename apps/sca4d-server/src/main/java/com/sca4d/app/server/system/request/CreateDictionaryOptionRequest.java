package com.sca4d.app.server.system.request;

import com.sca4d.core.api.i18n.LocalizedFields;
import com.sca4d.core.spring.validation.Req;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Objects;

/** 接收创建数据库字典项时提交的稳定值、标签、状态和顺序。 */
public final class CreateDictionaryOptionRequest {

    @NotBlank
    @Size(max = 160)
    @Pattern(regexp = "[A-Za-z0-9][A-Za-z0-9._:-]{0,159}")
    private String value;

    @NotBlank
    @Size(max = 160)
    private String label;

    @Req.NotNull
    private @Nullable LocalizedFields i18n;

    private boolean enabled = true;

    @Min(0)
    @Max(1_000_000)
    private int sortOrder;

    /** 创建等待 Jackson 写入字段的请求。 */
    public CreateDictionaryOptionRequest() {
    }

    /** @return 提交给业务接口的稳定值 */
    public @NotNull String getValue() {
        return value;
    }

    /** @param value 提交给业务接口的稳定值 */
    public void setValue(@NotNull String value) {
        this.value = value;
    }

    /** @return 管理端维护的展示标签 */
    public @NotNull String getLabel() {
        return label;
    }

    /** @param label 管理端维护的展示标签 */
    public void setLabel(@NotNull String label) {
        this.label = label;
    }

    /** @return 按 Locale 保存的完整多语言 label，可以不包含任何 Locale */
    public @NotNull LocalizedFields getI18n() {
        return Objects.requireNonNull(i18n, "字典项多语言标签不能为空");
    }

    /** @param i18n 按 Locale 保存的完整多语言 label */
    public void setI18n(@Nullable LocalizedFields i18n) {
        this.i18n = i18n;
    }

    /** @return 创建后允许用于新输入时返回 {@code true} */
    public boolean isEnabled() {
        return enabled;
    }

    /** @param enabled 创建后是否允许用于新输入 */
    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    /** @return 同一字典内的展示顺序 */
    public int getSortOrder() {
        return sortOrder;
    }

    /** @param sortOrder 同一字典内的展示顺序 */
    public void setSortOrder(int sortOrder) {
        this.sortOrder = sortOrder;
    }
}
