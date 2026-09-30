package com.sca4d.core.api.region;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Objects;

/** 保存已经确认有效且使用规范 E.164 表示的电话号码及其地区元数据。 */
public class ParsedPhoneNumber {

    private final @Nullable Continent continent;

    private final PhoneNumberType type;

    private final @Nullable Region region;

    private final String value;

    private final int countryCallingCode;

    /** 创建只由 RegionCatalog 返回的已解析号码。 */
    ParsedPhoneNumber(
            @NotNull String value,
            @Nullable Region region,
            @Nullable Continent continent,
            int countryCallingCode,
            @NotNull PhoneNumberType type) {
        this.value = Objects.requireNonNull(value, "规范电话号码不能为空");
        this.region = region;
        this.continent = continent;
        this.countryCallingCode = countryCallingCode;
        this.type = Objects.requireNonNull(type, "电话号码类型不能为空");
    }

    /**
     * 已经通过有效性和格式检查的号码。
     *
     * @return E.164 格式电话号码
     */
    public @NotNull String value() {
        return value;
    }

    /**
     * libphonenumber 从号码确定的地区。
     *
     * @return 两位地区代码对应的 `Region`；非地理号码为 `Region("001")`，无法确定时为空
     */
    public @Nullable Region region() {
        return region;
    }

    /**
     * 地区代码对应的洲别。
     *
     * @return 地理号码所属洲别；非地理号码或无法确定时为空
     */
    public @Nullable Continent continent() {
        return continent;
    }

    /**
     * E.164 号码中的国家或地区拨号前缀。
     *
     * @return 正整数国际电话代码
     */
    public int countryCallingCode() {
        return countryCallingCode;
    }

    /**
     * libphonenumber 判断的号码用途。
     *
     * @return 固话、移动电话等电话号码类型
     */
    public @NotNull PhoneNumberType type() {
        return type;
    }
}
