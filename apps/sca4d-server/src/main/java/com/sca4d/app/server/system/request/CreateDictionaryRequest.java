package com.sca4d.app.server.system.request;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import org.jetbrains.annotations.NotNull;

/** 接收创建动态字典时提交的稳定键、名称、状态和顺序。 */
public class CreateDictionaryRequest {

    @NotBlank
    @Size(max = 160)
    @Pattern(regexp = "[a-z][a-z0-9]*(?:[.-][a-z0-9]+)+")
    private String key;

    @NotBlank
    @Size(max = 160)
    private String label;

    private boolean enabled = true;

    @Min(0)
    @Max(1_000_000)
    private int sortOrder;

    /** 创建等待 Jackson 写入字段的请求。 */
    public CreateDictionaryRequest() {
    }

    /** @return 动态字典全局稳定键 */
    public @NotNull String getKey() {
        return key;
    }

    /** @param key 动态字典全局稳定键 */
    public void setKey(@NotNull String key) {
        this.key = key;
    }

    /** @return 动态字典展示名称 */
    public @NotNull String getLabel() {
        return label;
    }

    /** @param label 动态字典展示名称 */
    public void setLabel(@NotNull String label) {
        this.label = label;
    }

    /** @return 创建后允许业务查询时返回 {@code true} */
    public boolean isEnabled() {
        return enabled;
    }

    /** @param enabled 创建后是否允许业务查询 */
    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    /** @return 管理端展示顺序 */
    public int getSortOrder() {
        return sortOrder;
    }

    /** @param sortOrder 管理端展示顺序 */
    public void setSortOrder(int sortOrder) {
        this.sortOrder = sortOrder;
    }
}
