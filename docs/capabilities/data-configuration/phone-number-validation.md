# @PhoneNumber

源码：`core/sca4dj-core-spring/src/main/java/com/sca4d/core/spring/validation/PhoneNumber.java`

本页涵盖 `@PhoneNumber` 的校验规则、DNF 规则组合和配置失败的结果。本页适用于在请求对象中声明电话号码字段的开发者。读完后，开发者能确定哪些输入会通过校验，以及校验结果不能证明什么。

## 基础规则

`@PhoneNumber` 只接受 [RegionCatalog](/capabilities/data-configuration/region-and-phone) 能解析，且输入文本本身已经是规范 E.164 的号码，例如 `+8613800138000`。

`+86 138 0013 8000`、`13800138000` 等可被人理解、但不是规范 E.164 的输入，不会被自动整理，直接校验失败。

`null` 和空白值视为未提供。必填输入同时使用 `Req.NotBlank`。

解析和地区数据经 RegionCatalog 提供，各模块不自行引入电话号码库。

## DNF 规则

`anyOf` 使用析取范式。多个 Rule 以 OR 连接，同一 Rule 内所有非空条件以 AND 连接。

|                       Rule 属性                        |                                                               含义                                                               |
|------------------------------------------------------|--------------------------------------------------------------------------------------------------------------------------------|
| `regions` / `excludeRegions`                         | 包含或排除 libphonenumber 地区代码，例如 `CN`、`US`                                                                                         |
| `continents` / `excludeContinents`                   | 包含或排除 Core Continent                                                                                                         |
| `countryCallingCodes` / `excludeCountryCallingCodes` | 包含或排除正整数国际电话代码，例如 `86`、`1`                                                                                                     |
| `types` / `excludeTypes`                             | 包含或排除 Core [PhoneNumberType](/javadoc/core/sca4dj-core-api/com/sca4d/core/api/region/PhoneNumberType.html)                  |
| `conditions`                                         | 必须全部通过的应用 [PhoneNumberCondition](/javadoc/core/sca4dj-core-api/com/sca4d/core/api/region/PhoneNumberCondition.html) Bean 类型 |

空 `anyOf` 接受任意有效的规范 E.164 号码。一个空 Rule 没有额外限制，因此该分支恒为真。

## 基本用法

必填的任意国际号码：

```java
@Req.NotBlank
@PhoneNumber
private String phoneNumber;
```

接受中国号码，或国际电话代码为 `1` 的美国移动或固话号码：

```java
@PhoneNumber(anyOf = {
        @PhoneNumber.Rule(regions = "CN"),
        @PhoneNumber.Rule(
                regions = "US",
                countryCallingCodes = 1,
                types = {
                        PhoneNumberType.MOBILE,
                        PhoneNumberType.FIXED_LINE,
                        PhoneNumberType.FIXED_LINE_OR_MOBILE
                })
})
private String contactNumber;
```

排除中国的亚洲号码：

```java
@PhoneNumber(anyOf = @PhoneNumber.Rule(
        continents = Continent.ASIA,
        excludeRegions = "CN"))
private String overseasAsianNumber;
```

## 示例

以下示例在工作区成员邀请请求中校验联系电话。仅接受中国大陆手机号，并通过自定义条件排除应用不接受的号码段。`ExcludedPrefixCondition` 由应用定义，实现 `PhoneNumberCondition`。

```java
public class InviteMemberRequest {

    @Req.NotBlank
    @SafeText(max = 64)
    private String displayName;

    @Req.NotBlank
    @PhoneNumber(anyOf = @PhoneNumber.Rule(
            regions = "CN",
            types = PhoneNumberType.MOBILE,
            conditions = ExcludedPrefixCondition.class))
    private String phoneNumber;
}
```

```java
@Component
class ExcludedPrefixCondition implements PhoneNumberCondition {

    @Override
    public boolean test(ParsedPhoneNumber phoneNumber) {
        return !phoneNumber.value().startsWith("+86170");
    }
}
```

客户端提交 `13800138000` 时，输入不是规范 E.164，返回 HTTP 400 和错误码 `1100`，`data` 中的 `field` 为 `phoneNumber`。

## 扩展方式

应用实现 `PhoneNumberCondition` 并注册为 Spring Bean，在 `@PhoneNumber.Rule(conditions = ...)` 中引用类型。校验器初始化时按类型从容器取得 Bean。同一类型没有 Bean 或存在多个 Bean 时，初始化失败。

## 配置失败与适用范围

以下情况下，Validator 初始化失败：

- 地区代码为空白；
- 国际电话代码小于等于零；
- 自定义条件类型没有且只有一个 Spring Bean。

普通无效号码只产生 Bean Validation 失败。

校验结果只表示号码语法、规范 E.164 表示和当前 libphonenumber 元数据匹配。它不表示号码已分配、真实存在、属于当前主体、可以接通或能够接收短信。
