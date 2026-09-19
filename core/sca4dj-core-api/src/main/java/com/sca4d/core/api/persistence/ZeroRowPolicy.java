package com.sca4d.core.api.persistence;

/** 声明 Repository 的单行写入影响零行时应当得到的持久化结果。 */
public enum ZeroRowPolicy {
    /** 零行不符合单行写入要求，报告影响行数异常。 */
    ERROR,

    /** 零行是幂等写入或可选条件写入的正常结果。 */
    ALLOW,

    /** 零行表示内部对象的乐观版本已经变化。 */
    OPTIMISTIC_LOCK,

    /** 零行表示调用方提交的版本前置条件已经失效。 */
    VERSION_PRECONDITION
}
