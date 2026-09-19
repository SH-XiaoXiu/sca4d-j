package com.sca4d.core.api.region;

import com.google.i18n.phonenumbers.NumberParseException;
import com.google.i18n.phonenumbers.PhoneNumberUtil;
import com.google.i18n.phonenumbers.Phonenumber;
import org.jetbrains.annotations.CheckReturnValue;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.Unmodifiable;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeSet;

/** 查询 libphonenumber 地区代码、国际电话代码和洲别之间的对应关系。 */
public final class RegionCatalog {

    private static final String NON_GEOGRAPHICAL_REGION = "001";

    private static final Map<String, Continent> CONTINENTS = continents();

    private final PhoneNumberUtil phoneNumberUtil;

    /** 创建使用指定 libphonenumber 元数据的地区目录。 */
    RegionCatalog(@NotNull PhoneNumberUtil phoneNumberUtil) {
        this.phoneNumberUtil = Objects.requireNonNull(phoneNumberUtil, "PhoneNumberUtil 不能为空");
        Set<String> missing = new LinkedHashSet<>(phoneNumberUtil.getSupportedRegions());
        missing.removeAll(CONTINENTS.keySet());
        if (!missing.isEmpty()) {
            throw new IllegalStateException("洲别元数据缺少 libphonenumber 地区: " + missing);
        }
    }

    /**
     * 使用当前 libphonenumber 单例元数据创建目录。
     *
     * @return 已检查所有支持地区均有洲别数据的新目录
     */
    @CheckReturnValue
    public static @NotNull RegionCatalog standard() {
        return new RegionCatalog(PhoneNumberUtil.getInstance());
    }

    /**
     * 读取地区所属洲别。
     *
     * @param regionCode ISO 3166-1 两位地区代码；不区分大小写
     * @return 洲别；{@code 001} 或未知地区返回 {@code null}
     */
    public @Nullable Continent continentOf(@NotNull String regionCode) {
        String normalized = normalize(regionCode);
        if (NON_GEOGRAPHICAL_REGION.equals(normalized)) {
            return null;
        }
        return CONTINENTS.get(normalized);
    }

    /**
     * 读取指定洲包含的 libphonenumber 地区代码。
     *
     * @param continent 洲别
     * @return 按地区代码排序的不可变集合
     */
    @SuppressWarnings("unused")
    public @Unmodifiable @NotNull Set<String> regionsOf(@NotNull Continent continent) {
        Objects.requireNonNull(continent, "洲别不能为空");
        Set<String> regions = new TreeSet<>();
        CONTINENTS.forEach((region, value) -> {
            if (value == continent && phoneNumberUtil.getSupportedRegions().contains(region)) {
                regions.add(region);
            }
        });
        return Collections.unmodifiableSet(regions);
    }

    /**
     * 读取地区使用的国际电话代码。
     *
     * @param regionCode ISO 3166-1 两位地区代码；不区分大小写
     * @return 正整数国际电话代码；未知地区返回 0
     */
    public int countryCallingCode(@NotNull String regionCode) {
        return phoneNumberUtil.getCountryCodeForRegion(normalize(regionCode));
    }

    /**
     * 解析有效且已经使用规范 E.164 表示的电话号码。
     *
     * @param value 待解析文本
     * @return 已解析号码；格式、号码元数据或 E.164 表示不符合要求时为空
     */
    public @Nullable ParsedPhoneNumber parse(@NotNull String value) {
        Objects.requireNonNull(value, "电话号码不能为空");
        try {
            Phonenumber.PhoneNumber number = phoneNumberUtil.parse(value, "ZZ");
            if (!phoneNumberUtil.isValidNumber(number)
                    || !value.equals(phoneNumberUtil.format(number, PhoneNumberUtil.PhoneNumberFormat.E164))) {
                return null;
            }
            String region = phoneNumberUtil.getRegionCodeForNumber(number);
            return new ParsedPhoneNumber(
                    value,
                    region == null ? null : new Region(region),
                    region == null ? null : continentOf(region),
                    number.getCountryCode(),
                    PhoneNumberType.valueOf(phoneNumberUtil.getNumberType(number).name()));
        } catch (NumberParseException exception) {
            return null;
        }
    }

    /** 规范化地区代码并拒绝空白。 */
    private static String normalize(String regionCode) {
        Objects.requireNonNull(regionCode, "地区代码不能为空");
        String normalized = regionCode.strip().toUpperCase(Locale.ROOT);
        if (normalized.isEmpty()) {
            throw new IllegalArgumentException("地区代码不能为空白");
        }
        return normalized;
    }

    /** 创建所有 libphonenumber 地理地区到洲别的索引。 */
    private static Map<String, Continent> continents() {
        Map<String, Continent> continents = new LinkedHashMap<>();
        put(
                continents,
                Continent.AFRICA,
                "AC DZ AO BJ BW BF BI CV CM CF TD KM CG CD CI DJ EG GQ ER SZ ET GA GM GH GN GW KE LS LR LY MG MW ML MR MU YT MA MZ NA NE NG RE RW SH ST SN SC SL SO ZA SS SD TA TZ TG TN UG EH ZM ZW");
        put(continents, Continent.ANTARCTICA, "AQ BV GS HM TF");
        put(
                continents,
                Continent.ASIA,
                "AF AM AZ BH BD BT IO BN KH CN CX CC CY DG GE HK IN ID IR IQ IL JP JO KZ KP KR KW KG LA LB MO MY MV MN MM NP OM PK PS PH QA SA SG LK SY TW TJ TH TL TM TR AE UZ VN YE");
        put(
                continents,
                Continent.EUROPE,
                "AX AL AD AT BY BE BA BG HR CZ DK EE FO FI FR DE GI GR GG VA HU IS IE IM IT JE LV LI LT LU MT MD MC ME NL MK NO PL PT RO RU SM RS SK SI ES SJ SE CH UA GB XK");
        put(
                continents,
                Continent.NORTH_AMERICA,
                "AG AI AW BS BB BZ BM BQ CA KY CR CU CW DM DO SV GL GD GP GT HT HN JM MQ MX MS NI PA PR BL KN LC MF PM VC SX TT TC US VG VI");
        put(
                continents,
                Continent.OCEANIA,
                "AS AU CK FJ PF GU KI MH FM NR NC NZ NU NF MP PW PG PN WS SB TK TO TV UM VU WF");
        put(continents, Continent.SOUTH_AMERICA, "AR BO BR CL CO EC FK GF GY PY PE SR UY VE");
        return Collections.unmodifiableMap(continents);
    }

    /** 把一组空格分隔的地区代码登记到指定洲别。 */
    private static void put(Map<String, Continent> continents, Continent continent, String regions) {
        for (String region : regions.split(" ")) {
            Continent previous = continents.put(region, continent);
            if (previous != null) {
                throw new IllegalStateException("地区重复登记洲别: " + region);
            }
        }
    }
}
