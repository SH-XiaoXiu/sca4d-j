package com.sca4d.system.view;

import com.sca4d.system.audit.OperationLogOperatorOption;
import lombok.Data;
import lombok.experimental.Accessors;
import org.jetbrains.annotations.NotNull;

/** 操作日志操作人远程选择项。 */
@Data
@Accessors(chain = true)
public class OperationLogOperatorOptionView {

    private Long id;
    private String label;

    /**
     * 从操作发生时保存的 Subject 快照构造候选项。
     *
     * @param option 已按历史日志去重的操作人快照
     * @return 供远程选择器使用的固定 ID 与业务标签
     */
    public static @NotNull OperationLogOperatorOptionView from(@NotNull OperationLogOperatorOption option) {
        return new OperationLogOperatorOptionView()
                .setId(option.getSubjectId())
                .setLabel(option.getSubjectDisplayName());
    }
}
