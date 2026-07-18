# 系统字典

系统字典统一提供稳定值、当前语言标签、语义色调、可选择状态和展示顺序。字典的使用语义由 `kind` 表达，稳定事实的维护方由 `source` 表达。

## 字典语义与来源

| kind | source | 用途 | 管理规则 |
| --- | --- | --- | --- |
| `CODE_ENUM` | `CODE` | 由 Java 枚举完整定义的闭集选项 | 类型和项内容只读，允许调整排序 |
| `BUSINESS_CODE` | `CODE` | 代码声明流程保留值、数据库维护普通 code 的开放字典 | 类型展示属性可编辑；允许新增普通项 |
| `BUSINESS_CODE` | `CONFIGURATION` | 完全由平台或租户维护的稳定业务编码 | 稳定值创建后不可修改 |
| `INPUT_SUGGESTION` | `CONFIGURATION` | 允许自由输入时提供快捷建议 | 类型和值均可维护 |

代码来源的可扩展字典中，保留项使用 `source=CODE`，普通项使用 `source=CONFIGURATION`。保留项的 value 和删除操作由代码锁定；标签、色调、状态、可选择状态和排序由管理端维护。

普通 `BaseEnum` 不产生字典行为。只有显式声明 `@CodeDictionary` 的枚举会被扫描、校验、写入数据库并向统一选项接口发布。

## 声明闭集字典

`CLOSED` 是默认模式，枚举声明全部公开选项：

```java
@CodeDictionary(
        code = "order.status",
        nameKey = "dictionary.order.status")
public enum OrderStatus implements BaseEnum {
    @CodeDictionaryItem(tone = DictionaryTone.INFO)
    PENDING,
    @CodeDictionaryItem(tone = DictionaryTone.SUCCESS)
    COMPLETED;

    @Override
    public @NotNull String value() {
        return name();
    }

    @Override
    public @NotNull String description() {
        return name();
    }
}
```

类型和字典项创建后追加到同级末尾。管理端加载完整轻量排序列表并通过拖拽调整；保存时提交完整 ID 和版本顺序，服务端在单次事务中规范化内部排序值。后续代码同步保留数据库中的人工排序。

## 声明可扩展字典

`EXTENSIBLE` 枚举只包含需要参与代码流程的保留值。管理端可以在同一字典中增加其他业务编码：

```java
@CodeDictionary(
        code = "customer.type",
        nameKey = "dictionary.customer.type",
        mode = CodeDictionaryMode.EXTENSIBLE)
public enum ReservedCustomerType implements BaseEnum {
    VIP,
    INTERNAL;

    @Override
    public @NotNull String value() {
        return name();
    }

    @Override
    public @NotNull String description() {
        return name();
    }
}
```

业务字段仍保存和传输字符串 code。流程需要识别保留值时使用枚举的 `value()` 比较；数据库中未进入枚举的 `PARTNER`、`WHOLESALE` 等值仍是合法业务编码。

## 隐藏枚举常量

同一 Java 枚举中的内部常量可以保留类型能力而不形成字典项：

```java
@CodeDictionaryItem(published = false)
INTERNAL_RETRY
```

隐藏常量仍参与值格式和大小写无关唯一性校验，但不要求国际化消息、不写入数据库，也不由选项接口返回。

## 标签与同步

闭集字典的名称键、标签键、色调和可选择状态由代码维护。可扩展字典使用代码元数据创建类型和保留项，之后按当前请求语言优先读取后台 labels；当前语言未维护时回退代码 `labelKey`。

应用启动时使用 PostgreSQL 事务级 advisory lock 串行同步代码目录：

- 新发布的类型和保留项自动写入。
- 可扩展字典中已存在的同值普通项自动转为代码保留项，并保留展示配置。
- 闭集声明取消或常量停止发布时，对应投影停用并逻辑删除。
- 可扩展声明取消或保留值停止发布时，对应类型或项释放为配置来源，已有业务 code 和展示内容继续保留。
- 重复类型编码、大小写冲突值、非法格式、错误 kind 占用和已发布文案缺失会阻止应用完成启动。

## 读取接口

平台、平台代管租户和租户工作区均通过各自字典根路径的 `GET /options?codes=...` 批量读取选项。单次最多请求 50 个编码；类型和字典项查询数量不随字典数量增长。

返回选项包含 `value`、`labelKey`、当前语言 `label`、`tone` 和 `disabled`。字典或字典项停用、不可选择时，历史值仍可展示，但不能用于新输入。

字典名称和字典项标签通过 `GET /system/languages` 返回的语言目录编辑。编辑器一次只展示一种语言输入，切换语言不会展开全部输入框；目录中标记为 `required` 的语言必须填写。
