# 数据模型与迁移

公告表保存所属租户、创建 Principal 和最近发布 Principal。平台管理员、租户管理员等角色名称不进入业务表。

## 前置条件

- PostgreSQL 已启动。
- 已确认 `4000–4099` 错误码号段未被其他模块使用。
- Java 包 `com.sca4d.announcement` 尚未存在，或正在按本示例创建。

## 确定公告所属租户

平台公告与租户公告使用同一张表：

- `tenant_id IS NULL`：平台公告。
- `tenant_id = 1001`：租户 1001 的公告。

不建立 `platform_announcement` 和 `tenant_announcement` 两张结构相同的表，也不保存 `publisher_role`。操作者类型可以通过 Principal 查询，不需要在公告表重复保存。

## 公告表

创建 `announcement` 表：

```sql
CREATE TABLE announcement (
    id BIGINT PRIMARY KEY,
    tenant_id BIGINT,
    title VARCHAR(120) NOT NULL,
    content TEXT NOT NULL,
    status VARCHAR(32) NOT NULL DEFAULT 'DRAFT',
    created_by_principal_id BIGINT NOT NULL,
    published_by_principal_id BIGINT,
    published_at TIMESTAMP WITH TIME ZONE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    version BIGINT NOT NULL DEFAULT 0,
    deleted SMALLINT NOT NULL DEFAULT 0
);

CREATE INDEX idx_announcement_management
    ON announcement (tenant_id, deleted, status, created_at DESC, id DESC);

CREATE INDEX idx_announcement_published
    ON announcement (tenant_id, published_at DESC, id DESC)
    WHERE deleted = 0 AND status = 'PUBLISHED';

COMMENT ON TABLE announcement IS '平台与租户公告';
COMMENT ON COLUMN announcement.tenant_id IS '归属租户；平台公告为空';
COMMENT ON COLUMN announcement.created_by_principal_id IS '创建公告的 Principal';
COMMENT ON COLUMN announcement.published_by_principal_id IS '最近发布公告的 Principal';
COMMENT ON COLUMN announcement.published_at IS '最近一次发布时间';
COMMENT ON COLUMN announcement.version IS '管理写入使用的乐观锁版本';
COMMENT ON COLUMN announcement.deleted IS '逻辑删除标记';
```

`created_by_principal_id` 和 `published_by_principal_id` 都保存 Principal 主键。它们不分别命名为 platform_user_id、tenant_member_id，因为公告不需要知道操作者属于哪一种业务角色。

这些引用不建立数据库外键，状态也不使用 `CHECK` 约束。所属租户和状态由 Java 类型、Service、原子 SQL 和测试检查。租户停用后，TENANT Principal 无法进入该租户；已有公告仍保留在数据库中。

## 状态

```java
public enum AnnouncementStatus implements BaseEnum {
    DRAFT("草稿"),
    PUBLISHED("已发布"),
    CLOSED("已关闭");

    private final String value;
    private final String description;

    AnnouncementStatus(@NotNull String description) {
        this.value = BaseEnum.normalize(name());
        this.description = description;
    }

    @Override
    public @NotNull String value() {
        return value;
    }

    @Override
    public @NotNull String description() {
        return description;
    }
}
```

状态转换由 Service 判断：

```text
DRAFT     -> PUBLISHED
PUBLISHED -> CLOSED
CLOSED    -> PUBLISHED
```

发布 SQL 同时写入状态、发布时间和发布 Principal。关闭后保留最近发布信息，重新发布时覆盖为新的值。

## Entity

```java
@Data
@Accessors(chain = true)
@Table("announcement")
public class AnnouncementEntity {
    private Long id;
    private Long tenantId;
    private String title;
    private String content;
    private AnnouncementStatus status;
    private Long createdByPrincipalId;
    private Long publishedByPrincipalId;
    private OffsetDateTime publishedAt;
    private OffsetDateTime createdAt;
    private OffsetDateTime updatedAt;
    private Long version;
    private Integer deleted;
}
```

## 业务读取模型

```java
@Data
@Accessors(chain = true)
public class Announcement {
    private Long announcementId;
    private Long tenantId;
    private String title;
    private String content;
    private AnnouncementStatus status;
    private Long createdByPrincipalId;
    private Long publishedByPrincipalId;
    private OffsetDateTime publishedAt;
    private OffsetDateTime createdAt;
    private OffsetDateTime updatedAt;
    private Long version;

    public static @NotNull Announcement from(
            @NotNull AnnouncementEntity entity) {
        return new Announcement()
                .setAnnouncementId(entity.getId())
                .setTenantId(entity.getTenantId())
                .setTitle(entity.getTitle())
                .setContent(entity.getContent())
                .setStatus(entity.getStatus())
                .setCreatedByPrincipalId(entity.getCreatedByPrincipalId())
                .setPublishedByPrincipalId(entity.getPublishedByPrincipalId())
                .setPublishedAt(entity.getPublishedAt())
                .setCreatedAt(entity.getCreatedAt())
                .setUpdatedAt(entity.getUpdatedAt())
                .setVersion(entity.getVersion());
    }
}
```

产品端 View 不返回管理状态、版本和操作者主键；管理端 View 返回 tenantId、状态、操作者与版本。

## 业务错误码

```java
@CodeRange(start = 4000, end = 4099)
public enum AnnouncementErrorCode implements BizErrorCode {
    ANNOUNCEMENT_NOT_FOUND(
            4000, "error.announcement.not-found", ResponseCode.NOT_FOUND),
    ANNOUNCEMENT_NOT_PUBLISHED(
            4001, "error.announcement.not-published", ResponseCode.NOT_FOUND),
    ANNOUNCEMENT_STATUS_CONFLICT(
            4002, "error.announcement.status-conflict", ResponseCode.CONFLICT);

    private final int code;
    private final String messageKey;
    private final ResponseCode category;

    AnnouncementErrorCode(
            int code,
            @NotNull String messageKey,
            @NotNull ResponseCode category) {
        this.code = code;
        this.messageKey = messageKey;
        this.category = category;
    }

    @Override
    public int getCode() {
        return code;
    }

    @Override
    public @NotNull String getMessageKey() {
        return messageKey;
    }

    @Override
    public @NotNull ResponseCode getCategory() {
        return category;
    }
}
```

在 `i18n/error.properties` 中添加默认简体中文消息：

```properties
error.announcement.not-found=公告不存在
error.announcement.not-published=公告不存在
error.announcement.status-conflict=公告状态不允许当前操作
```

在 `i18n/error_en_US.properties` 中添加英文消息：

```properties
error.announcement.not-found=Announcement not found
error.announcement.not-published=Announcement not found
error.announcement.status-conflict=The announcement status does not allow this operation
```

产品端对不存在、其他租户、草稿、关闭和已删除公告使用相同的“公告不存在”，不暴露目标公告是否真实存在。管理查询对范围外记录返回不存在；原子写入无法命中范围或版本时统一返回冲突。

## 检查结果

使用空数据库启动主应用。Flyway 应创建 `announcement` 表及索引，Java 枚举可以按数据库字符串读写，错误码扫描不报告号段冲突。失败时检查迁移版本、错误码号段以及 `i18n/error.properties` 中的 message key。

[Mapper 与 Repository](./announcement-persistence)使用 tenantId 限制查询和修改范围。
