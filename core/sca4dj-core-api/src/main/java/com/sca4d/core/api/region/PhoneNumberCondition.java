package com.sca4d.core.api.region;

import org.jetbrains.annotations.NotNull;

/** 判断已经由 RegionCatalog 解析并确认有效的电话号码是否满足自定义条件。 */
@FunctionalInterface
public interface PhoneNumberCondition {

    /**
     * 判断电话号码是否满足应用定义的条件。
     *
     * @param phoneNumber 已解析且有效的规范 E.164 电话号码及地区元数据
     * @return 满足条件时返回 {@code true}
     */
    boolean test(@NotNull ParsedPhoneNumber phoneNumber);
}
