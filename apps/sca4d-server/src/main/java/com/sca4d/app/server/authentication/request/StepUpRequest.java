package com.sca4d.app.server.authentication.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Objects;

/** 为当前 Session 创建绑定敏感操作的 STEP_UP 尝试。 */
public final class StepUpRequest {

    @NotBlank
    @Size(max = 128)
    private @Nullable String actionCode;

    /** 创建供 JSON 反序列化使用的空请求。 */
    public StepUpRequest() {
    }

    /**
     * 返回 STEP_UP 绑定的操作编码。
     *
     * @return 将由最终敏感操作复核的稳定操作编码
     */
    public @NotNull String getActionCode() {
        return Objects.requireNonNull(actionCode, "STEP_UP 操作编码不能为空");
    }

    /**
     * 设置 STEP_UP 绑定的操作编码。
     *
     * @param actionCode 稳定操作编码
     */
    @SuppressWarnings("unused") // Jackson 通过 JavaBean setter 反序列化请求。
    public void setActionCode(@Nullable String actionCode) {
        this.actionCode = actionCode;
    }
}
