package com.sca4d.core.api.name.code;

/**
 * 规定同一个对象在一条 {@link AttributeAxis} 上可以携带多少个属性值。
 */
public enum AttributeConstraint {

    /** 可以不携带，也可以携带多个属性值。 */
    OPTIONAL,

    /** 必须且只能携带一个属性值。 */
    REQUIRED_EXCLUSIVE
}
