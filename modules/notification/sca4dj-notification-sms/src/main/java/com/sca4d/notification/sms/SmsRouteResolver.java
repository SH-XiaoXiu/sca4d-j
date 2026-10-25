package com.sca4d.notification.sms;

import com.sca4d.core.api.name.code.TreeCode;
import com.sca4d.core.api.region.ParsedPhoneNumber;
import com.sca4d.core.api.region.Region;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Locale;

/**
 * 根据具体应用的业务区域和电话目的地规则选择 SMS Provider 路由。
 */
@FunctionalInterface
public interface SmsRouteResolver {

    /**
     * @param code        通知编码
     * @param region      通知发生或面向的业务区域；没有区域信息时为空
     * @param destination 已经确认有效的 E.164 电话目的地
     * @param locale      通知最终选择的 Locale
     * @return 具体应用声明的 SMS 路由
     */
    @NotNull
    SmsRoute resolve(
            @NotNull TreeCode code,
            @Nullable Region region,
            @NotNull ParsedPhoneNumber destination,
            @NotNull Locale locale);
}
