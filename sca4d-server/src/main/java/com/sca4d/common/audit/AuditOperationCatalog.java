package com.sca4d.common.audit;

import com.sca4d.common.validation.ValidationPatterns;
import org.jetbrains.annotations.NotNull;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 当前应用全部可审计模块和动作的启动期目录。
 *
 * <p>目录在 Bean 创建时一次遍历所有业务模块贡献项，拒绝非法编码、空消息键、重复动作以及同一模块不一致的名称或排序。运行期查询只访问
 * 不可变内存索引，不执行数据库或外部调用。
 */
@Component
public class AuditOperationCatalog {

    private final List<AuditOperationDefinition> operations;
    private final Map<String, AuditOperationDefinition> operationByCode;

    /**
     * 汇总并校验当前应用注册的审计动作。
     *
     * @param contributors 各业务模块提供的审计动作目录
     * @throws IllegalStateException 任一定义非法、重复或模块元数据不一致时抛出并阻止应用启动
     */
    public AuditOperationCatalog(@NotNull List<AuditOperationContributor> contributors) {
        Map<String, AuditOperationDefinition> registered = new LinkedHashMap<>();
        Map<String, AuditOperationDefinition> moduleDefinitions = new LinkedHashMap<>();
        for (AuditOperationContributor contributor : contributors) {
            List<AuditOperationDefinition> definitions = contributor.auditOperations();
            if (definitions == null) {
                throw new IllegalStateException(
                        "审计目录扩展返回空集合对象: " + contributor.getClass().getName());
            }
            for (AuditOperationDefinition definition : definitions) {
                if (definition == null
                        || !ValidationPatterns.isNamespacedCode(definition.getModuleCode())
                        || !ValidationPatterns.isNamespacedCode(definition.getActionCode())
                        || !StringUtils.hasText(definition.getModuleNameKey())
                        || !StringUtils.hasText(definition.getActionNameKey())
                        || !StringUtils.hasText(definition.getSummaryKey())) {
                    throw new IllegalStateException(
                            "审计目录包含非法定义: " + contributor.getClass().getName());
                }
                AuditOperationDefinition module = moduleDefinitions.putIfAbsent(
                        definition.getModuleCode(), definition);
                if (module != null
                        && (!module.getModuleNameKey().equals(definition.getModuleNameKey())
                                || module.getModuleSortOrder() != definition.getModuleSortOrder())) {
                    throw new IllegalStateException(
                            "同一审计模块的名称或排序不一致: " + definition.getModuleCode());
                }
                String operationCode = definition.getModuleCode() + ":" + definition.getActionCode();
                if (registered.putIfAbsent(operationCode, definition) != null) {
                    throw new IllegalStateException("重复审计动作定义: " + operationCode);
                }
            }
        }
        List<AuditOperationDefinition> sorted = new ArrayList<>(registered.values());
        sorted.sort(Comparator.comparingInt(AuditOperationDefinition::getModuleSortOrder)
                .thenComparing(AuditOperationDefinition::getModuleCode)
                .thenComparingInt(AuditOperationDefinition::getActionSortOrder)
                .thenComparing(AuditOperationDefinition::getActionCode));
        this.operations = List.copyOf(sorted);
        this.operationByCode = Map.copyOf(registered);
    }

    /**
     * 获取一个已经注册的审计动作定义。
     *
     * @param moduleCode 持久化审计记录中的模块编码
     * @param actionCode 持久化审计记录中的动作编码
     * @return 与两个固定编码精确匹配的目录定义
     * @throws IllegalStateException 代码声明或历史记录使用了目录外动作时抛出
     */
    public @NotNull AuditOperationDefinition require(
            @NotNull String moduleCode, @NotNull String actionCode) {
        AuditOperationDefinition operation = operationByCode.get(moduleCode + ":" + actionCode);
        if (operation == null) {
            throw new IllegalStateException("未注册的审计动作: " + moduleCode + ":" + actionCode);
        }
        return operation;
    }

    /**
     * 返回当前应用完整的审计动作目录。
     *
     * @return 按模块和动作展示顺序排列的不可变目录
     */
    public @NotNull List<AuditOperationDefinition> operations() {
        return operations;
    }
}
