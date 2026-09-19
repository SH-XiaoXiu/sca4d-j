package com.sca4d.core.api.region;

/** 表示 libphonenumber 能稳定识别的电话号码用途类型。 */
public enum PhoneNumberType {

    /** 固定电话。 */
    FIXED_LINE,

    /** 移动电话。 */
    MOBILE,

    /** 元数据不能进一步区分固定电话和移动电话。 */
    FIXED_LINE_OR_MOBILE,

    /** 免费电话。 */
    TOLL_FREE,

    /** 付费服务电话。 */
    PREMIUM_RATE,

    /** 费用由呼叫方和接听方分担的电话。 */
    SHARED_COST,

    /** 网络电话。 */
    VOIP,

    /** 个人号码。 */
    PERSONAL_NUMBER,

    /** 寻呼号码。 */
    PAGER,

    /** 通用接入号码。 */
    UAN,

    /** 语音信箱号码。 */
    VOICEMAIL,

    /** libphonenumber 无法确定用途类型。 */
    UNKNOWN
}
