package com.sca4d.core.api.scheduling;

import com.sca4d.core.api.name.NameFormat;
import com.sca4d.core.api.name.code.Code;
import com.sca4d.core.api.name.code.TreeCode;
import org.jetbrains.annotations.NotNull;

import java.util.List;

/** 使用稳定节点链标识调度计划，并保留独立领域类型。 */
public final class ScheduleKey extends TreeCode {

    /** 从代码声明或序列化边界解析编码，拒绝通配符和超过 128 字符的名称。 */
    public ScheduleKey(@NotNull String text) {
        this(TreeCode.of(text));
    }

    /** 从已经校验的稳定编码构造领域名称。 */
    public ScheduleKey(@NotNull Code code) {
        super(code);
        if (hasNode(TreeCode.ofSegment("*")) || format(NameFormat.COLON).length() > 128) {
            throw new IllegalArgumentException("ScheduleKey 必须是最多 128 字符的固定编码");
        }
    }

    @Override
    protected @NotNull ScheduleKey withNodes(@NotNull List<String> nodes) {
        return new ScheduleKey(super.withNodes(nodes));
    }

    @Override
    public @NotNull ScheduleKey child(@NotNull String node) {
        return (ScheduleKey) super.child(node);
    }

    @Override
    public @NotNull ScheduleKey child(@NotNull TreeCode node) {
        return (ScheduleKey) super.child(node);
    }

    @Override
    public @NotNull ScheduleKey append(@NotNull Code code) {
        return (ScheduleKey) super.append(code);
    }
}
