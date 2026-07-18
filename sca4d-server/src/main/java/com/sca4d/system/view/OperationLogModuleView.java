package com.sca4d.system.view;

import com.sca4d.system.audit.OperationLogModuleDescription;
import lombok.Data;
import lombok.experimental.Accessors;
import org.jetbrains.annotations.NotNull;

import java.util.List;

/** 操作日志模块及其动作筛选目录。 */
@Data
@Accessors(chain = true)
public class OperationLogModuleView {

    private String code;
    private String name;
    private List<OperationLogActionView> actions;

    /** @return 从已本地化的模块说明构造接口视图 */
    public static @NotNull OperationLogModuleView from(@NotNull OperationLogModuleDescription module) {
        return new OperationLogModuleView()
                .setCode(module.getCode())
                .setName(module.getName())
                .setActions(module.getActions().stream().map(OperationLogActionView::from).toList());
    }
}
