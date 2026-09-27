package com.sca4d.app.server.system.request;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.jetbrains.annotations.NotNull;

/** 接收修改动态字典时提交的名称、状态、顺序和乐观锁版本。 */
public class UpdateDictionaryRequest {

    @NotBlank
    @Size(max = 160)
    private String label;

    private boolean enabled;

    @Min(0)
    @Max(1_000_000)
    private int sortOrder;

    @Min(0)
    private long version = -1;

    /** 创建等待 Jackson 写入字段的请求。 */
    public UpdateDictionaryRequest() {
    }

    /** @return 动态字典展示名称 */
    public @NotNull String getLabel() {
        return label;
    }

    /** @param label 动态字典展示名称 */
    public void setLabel(@NotNull String label) {
        this.label = label;
    }

    /** @return 修改后允许业务查询时返回 {@code true} */
    public boolean isEnabled() {
        return enabled;
    }

    /** @param enabled 修改后是否允许业务查询 */
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

    /** @return 客户端读取到的乐观锁版本 */
    public long getVersion() {
        return version;
    }

    /** @param version 客户端读取到的乐观锁版本 */
    public void setVersion(long version) {
        this.version = version;
    }
}
