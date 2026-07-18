package com.sca4d.system.audit;

import com.sca4d.common.audit.AuditOperationContributor;
import com.sca4d.common.audit.AuditOperationDefinition;
import org.jetbrains.annotations.NotNull;
import org.springframework.stereotype.Component;

import java.util.List;

/** 系统字典、菜单与 SMTP 配置管理操作的审计动作目录。 */
@Component
public class SystemAuditOperations implements AuditOperationContributor {

    /** 平台字典修改记录使用的审计模块编码。 */
    public static final String DICTIONARIES = "system.dictionaries";
    /** 平台菜单修改记录使用的审计模块编码。 */
    public static final String MENUS = "system.menus";
    /** SMTP 配置修改和连接测试使用的审计模块编码。 */
    public static final String MAIL_SENDERS = "system.mail-senders";
    /** 邮件模板修改和测试发送使用的审计模块编码。 */
    public static final String MAIL_TEMPLATES = "system.mail-templates";
    /** GLOBAL IdentityProvider 修改和连接测试使用的审计模块编码。 */
    public static final String IDENTITY_PROVIDERS = "system.identity-providers";
    /** 创建字典或菜单的动作编码。 */
    public static final String CREATE = "create";
    /** 修改字典或菜单内容的动作编码。 */
    public static final String UPDATE = "update";
    /** 删除字典或菜单的动作编码。 */
    public static final String DELETE = "delete";
    /** 在字典中创建选项的动作编码。 */
    public static final String CREATE_ITEM = "create-item";
    /** 修改字典选项内容的动作编码。 */
    public static final String UPDATE_ITEM = "update-item";
    /** 删除字典选项的动作编码。 */
    public static final String DELETE_ITEM = "delete-item";
    /** 修改字典类型排序的动作编码。 */
    public static final String CHANGE_SORT = "change-sort";
    /** 修改同一字典内选项排序的动作编码。 */
    public static final String CHANGE_ITEM_SORT = "change-item-sort";
    /** 移动菜单并重排同级节点的动作编码。 */
    public static final String REORDER = "reorder";
    /** 启用或停用系统配置的动作编码。 */
    public static final String CHANGE_STATUS = "change-status";
    /** 使用当前 SMTP 配置建立并关闭连接的动作编码。 */
    public static final String TEST_CONNECTION = "test-connection";
    /** 使用邮件模板向指定地址投递测试邮件的动作编码。 */
    public static final String TEST_SEND = "test-send";

    /**
     * 返回系统模块当前能够产生的全部审计动作。
     *
     * @return 系统字典和菜单管理的固定动作定义
     */
    @Override
    public @NotNull List<AuditOperationDefinition> auditOperations() {
        return List.of(
                AuditOperationDefinition.standard(DICTIONARIES, 70, CREATE, 10),
                AuditOperationDefinition.standard(DICTIONARIES, 70, UPDATE, 20),
                AuditOperationDefinition.standard(DICTIONARIES, 70, DELETE, 30),
                AuditOperationDefinition.standard(DICTIONARIES, 70, CREATE_ITEM, 40),
                AuditOperationDefinition.standard(DICTIONARIES, 70, UPDATE_ITEM, 50),
                AuditOperationDefinition.standard(DICTIONARIES, 70, DELETE_ITEM, 60),
                AuditOperationDefinition.standard(DICTIONARIES, 70, CHANGE_SORT, 70),
                AuditOperationDefinition.standard(DICTIONARIES, 70, CHANGE_ITEM_SORT, 80),
                AuditOperationDefinition.standard(MENUS, 80, CREATE, 10),
                AuditOperationDefinition.standard(MENUS, 80, UPDATE, 20),
                AuditOperationDefinition.standard(MENUS, 80, DELETE, 30),
                AuditOperationDefinition.standard(MENUS, 80, REORDER, 40),
                AuditOperationDefinition.standard(MAIL_SENDERS, 90, CREATE, 10),
                AuditOperationDefinition.standard(MAIL_SENDERS, 90, UPDATE, 20),
                AuditOperationDefinition.standard(MAIL_SENDERS, 90, CHANGE_STATUS, 30),
                AuditOperationDefinition.standard(MAIL_SENDERS, 90, TEST_CONNECTION, 40),
                AuditOperationDefinition.standard(MAIL_SENDERS, 90, DELETE, 50),
                AuditOperationDefinition.standard(MAIL_TEMPLATES, 95, CREATE, 10),
                AuditOperationDefinition.standard(MAIL_TEMPLATES, 95, UPDATE, 20),
                AuditOperationDefinition.standard(MAIL_TEMPLATES, 95, CHANGE_STATUS, 30),
                AuditOperationDefinition.standard(MAIL_TEMPLATES, 95, TEST_SEND, 40),
                AuditOperationDefinition.standard(MAIL_TEMPLATES, 95, DELETE, 50),
                AuditOperationDefinition.standard(IDENTITY_PROVIDERS, 97, CREATE, 10),
                AuditOperationDefinition.standard(IDENTITY_PROVIDERS, 97, UPDATE, 20),
                AuditOperationDefinition.standard(IDENTITY_PROVIDERS, 97, CHANGE_STATUS, 30),
                AuditOperationDefinition.standard(IDENTITY_PROVIDERS, 97, TEST_CONNECTION, 40),
                AuditOperationDefinition.standard(IDENTITY_PROVIDERS, 97, DELETE, 50));
    }
}
