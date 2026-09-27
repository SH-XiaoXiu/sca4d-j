package com.sca4d.core.api.job;

import com.sca4d.core.api.name.NameFormat;
import com.sca4d.core.api.name.code.Code;
import com.sca4d.core.api.name.code.TreeCode;
import org.jetbrains.annotations.NotNull;

import java.util.List;

/** 使用稳定节点链标识 Job 定义，并保留独立领域类型。 */
public final class JobKey extends TreeCode {

    /** 从代码声明或序列化边界解析编码，拒绝通配符和超过 128 字符的名称。 */
    public JobKey(@NotNull String text) {
        this(TreeCode.of(text));
    }

    /** 从已经校验的稳定编码构造领域名称。 */
    public JobKey(@NotNull Code code) {
        super(code);
        if (hasNode(TreeCode.ofSegment("*")) || format(NameFormat.COLON).length() > 128) {
            throw new IllegalArgumentException("JobKey 必须是最多 128 字符的固定编码");
        }
    }

    @Override
    protected @NotNull JobKey withNodes(@NotNull List<String> nodes) {
        return new JobKey(super.withNodes(nodes));
    }

    @Override
    public @NotNull JobKey child(@NotNull String node) {
        return (JobKey) super.child(node);
    }

    @Override
    public @NotNull JobKey child(@NotNull TreeCode node) {
        return (JobKey) super.child(node);
    }

    @Override
    public @NotNull JobKey append(@NotNull Code code) {
        return (JobKey) super.append(code);
    }
}
