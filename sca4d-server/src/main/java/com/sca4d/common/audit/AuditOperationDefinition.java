package com.sca4d.common.audit;

import org.jetbrains.annotations.NotNull;

/**
 * 一个可审计业务动作的固定目录定义。
 *
 * <p>模块和动作编码参与持久化、筛选和代码判断；消息键只负责当前语言的模块名、动作名和默认摘要展示。定义由所属业务模块通过
 * {@link AuditOperationContributor} 注册，不从历史日志数据反向推断。
 */
public class AuditOperationDefinition {

    private final String moduleCode;
    private final String moduleNameKey;
    private final int moduleSortOrder;
    private final String actionCode;
    private final String actionNameKey;
    private final String summaryKey;
    private final int actionSortOrder;

    /**
     * 创建遵循共用消息键命名约定的审计动作定义。
     *
     * @param moduleCode 所属业务模块声明的点分模块编码
     * @param moduleSortOrder 模块在筛选目录中的展示顺序
     * @param actionCode 模块内固定动作编码
     * @param actionSortOrder 动作在所属模块中的展示顺序
     * @return 模块名键为 {@code audit.module.<module>}、动作名键为 {@code audit.action.<action>}、摘要键为
     *     {@code audit.summary.<module>.<action>} 的目录定义
     */
    public static @NotNull AuditOperationDefinition standard(
            @NotNull String moduleCode, int moduleSortOrder, @NotNull String actionCode, int actionSortOrder) {
        return new AuditOperationDefinition(
                moduleCode,
                "audit.module." + moduleCode,
                moduleSortOrder,
                actionCode,
                "audit.action." + actionCode,
                "audit.summary." + moduleCode + "." + actionCode,
                actionSortOrder);
    }

    /**
     * 创建显式消息目录定义。
     *
     * @param moduleCode 所属业务模块声明的点分模块编码
     * @param moduleNameKey 模块展示消息键
     * @param moduleSortOrder 模块展示顺序
     * @param actionCode 模块内固定动作编码
     * @param actionNameKey 动作展示消息键
     * @param summaryKey 未被业务场景覆盖时使用的默认摘要消息键
     * @param actionSortOrder 动作展示顺序
     */
    public AuditOperationDefinition(
            @NotNull String moduleCode,
            @NotNull String moduleNameKey,
            int moduleSortOrder,
            @NotNull String actionCode,
            @NotNull String actionNameKey,
            @NotNull String summaryKey,
            int actionSortOrder) {
        this.moduleCode = moduleCode;
        this.moduleNameKey = moduleNameKey;
        this.moduleSortOrder = moduleSortOrder;
        this.actionCode = actionCode;
        this.actionNameKey = actionNameKey;
        this.summaryKey = summaryKey;
        this.actionSortOrder = actionSortOrder;
    }

    /** @return 参与审计持久化和筛选的点分模块编码 */
    public @NotNull String getModuleCode() {
        return moduleCode;
    }

    /** @return 模块展示消息键 */
    public @NotNull String getModuleNameKey() {
        return moduleNameKey;
    }

    /** @return 模块展示顺序 */
    public int getModuleSortOrder() {
        return moduleSortOrder;
    }

    /** @return 模块内固定动作编码 */
    public @NotNull String getActionCode() {
        return actionCode;
    }

    /** @return 动作展示消息键 */
    public @NotNull String getActionNameKey() {
        return actionNameKey;
    }

    /** @return 默认审计摘要消息键 */
    public @NotNull String getSummaryKey() {
        return summaryKey;
    }

    /** @return 动作展示顺序 */
    public int getActionSortOrder() {
        return actionSortOrder;
    }
}
