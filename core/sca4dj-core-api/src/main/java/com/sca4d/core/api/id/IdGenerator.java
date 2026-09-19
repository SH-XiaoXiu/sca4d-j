package com.sca4d.core.api.id;

/** 生成全局正整数标识。 */
public interface IdGenerator {

    /**
     * 返回下一个正整数标识。
     *
     * @return 下一个正整数标识
     */
    long nextId();
}
