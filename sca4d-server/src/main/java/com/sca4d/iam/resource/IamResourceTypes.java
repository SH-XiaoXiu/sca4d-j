package com.sca4d.iam.resource;

/** IAM 业务模块向共用资源引用注册的开放资源类型编码。 */
public final class IamResourceTypes {

    /** 登录账号管理资源。 */
    public static final String ACCOUNT = "account";
    /** 统一身份主档。 */
    public static final String SUBJECT = "subject";
    /** 租户管理资源。 */
    public static final String TENANT = "tenant";
    /** 由租户 Principal 表达的成员资源。 */
    public static final String TENANT_MEMBER = "tenant-member";
    /** 租户成员关系记录。 */
    public static final String MEMBERSHIP = "membership";
    /** 审计与授权变更直接指向的 Principal 资源。 */
    public static final String PRINCIPAL = "principal";
    /** 现有 Credential 记录表达的登录凭证资源。 */
    public static final String CREDENTIAL = "credential";
    /** 平台或租户精确范围中的角色定义资源。 */
    public static final String ROLE = "role";

    private IamResourceTypes() {
    }
}
