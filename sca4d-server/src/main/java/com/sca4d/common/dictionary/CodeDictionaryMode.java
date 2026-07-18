package com.sca4d.common.dictionary;

/** 代码字典是否允许管理端增加枚举声明之外的业务编码。 */
public enum CodeDictionaryMode {
    /** 枚举声明全部可发布值，数据库不能增加配置项。 */
    CLOSED,
    /** 枚举只声明操作保留值，数据库可以同时维护普通业务编码。 */
    EXTENSIBLE
}
