package com.sca4d.core.api.plugin;

/** 声明扩展实现的选择规则。 */
public enum ExtensionSelection {
    /** 同一契约只能有一个活动实现。 */
    SINGLE,
    /** 按实现编码选择，编码不得重复。 */
    KEYED
}
