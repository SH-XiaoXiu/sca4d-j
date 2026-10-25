# RegionCatalog

源码：`core/sca4dj-core-api/src/main/java/com/sca4d/core/api/region/RegionCatalog.java`

本页说明 Core 如何查询地区和电话号码元数据。本页适用于需要读取地区代码、国际电话代码或解析规范 E.164 号码的开发者。读完后，开发者能判断解析结果的适用范围，以及它不能证明的内容。

## 职责

RegionCatalog 是 Core 查询地区和电话号码元数据的入口。它封装 libphonenumber，并只向调用方返回 Core 或 JDK 类型。

|                方法                |                      行为                       |
|----------------------------------|-----------------------------------------------|
| `standard()`                     | 使用 libphonenumber 当前单例元数据创建目录                 |
| `continentOf(regionCode)`        | 查询 ISO 3166-1 两位地区代码所属洲别；`001` 或未知代码返回 `null` |
| `regionsOf(continent)`           | 返回该洲中 libphonenumber 支持的地区代码排序不可变集合           |
| `countryCallingCode(regionCode)` | 返回地区使用的国际电话代码；未知地区返回 `0`                      |
| `parse(value)`                   | 解析有效且输入本身为规范 E.164 的号码；不符合要求时返回 `null`        |

地区代码查询不区分大小写，并会去掉首尾空白。`null` 或空白代码会导致查询失败。

第三方库的类型只出现在 Core API 的实现内部，调用方只依赖 `ParsedPhoneNumber` 和稳定的返回值。

## 查询示例

```java
RegionCatalog regions = RegionCatalog.standard();

assert regions.continentOf("cn") == Continent.ASIA;
assert regions.countryCallingCode("CN") == 86;
assert regions.regionsOf(Continent.EUROPE).contains("GB");
```

## 示例

以下示例按成员的联系电话判断所在洲别。解析失败时由应用决定处理方式：

```java
@Service
class MemberRegionService {

    private final RegionCatalog regions = RegionCatalog.standard();

    Continent continentOf(String e164Number) {
        ParsedPhoneNumber parsed = regions.parse(e164Number);
        if (parsed == null) {
            throw new IllegalArgumentException("电话号码不是有效的规范 E.164 号码");
        }
        return parsed.continent();
    }
}
```

## 电话号码解析示例

`parse` 同时要求号码有效，且输入文本已经是规范 E.164：

```java
ParsedPhoneNumber parsed = regions.parse("+14155552671");
assert parsed != null;
assert parsed.countryCallingCode() == 1;

assert regions.parse("+1 415 555 2671") == null; // 含空格，不是规范 E.164
assert regions.parse("4155552671") == null;     // 缺少国际电话代码
```

目录构造时，检查内置洲别映射是否覆盖当前 libphonenumber 的全部地理地区。依赖升级后新增了地区，而 Core 未为其分类时，抛出 IllegalStateException。这样可以避免静默返回错误洲别。

libphonenumber 类型只在 Core API 实现内部使用。解析结果不验证号码分配、所有权、运营商当前状态或可达性。
