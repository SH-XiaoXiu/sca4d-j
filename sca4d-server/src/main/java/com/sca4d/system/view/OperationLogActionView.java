package com.sca4d.system.view;

import com.sca4d.system.audit.OperationLogActionDescription;
import lombok.Data;
import lombok.experimental.Accessors;
import org.jetbrains.annotations.NotNull;

/** 操作日志动作筛选项。 */
@Data
@Accessors(chain = true)
public class OperationLogActionView {

    private String code;
    private String name;

    /** @return 从已本地化的动作说明构造接口视图 */
    public static @NotNull OperationLogActionView from(@NotNull OperationLogActionDescription action) {
        return new OperationLogActionView().setCode(action.getCode()).setName(action.getName());
    }
}
