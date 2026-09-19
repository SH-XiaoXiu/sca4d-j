package com.sca4d.core.api.identity;

/** 表示请求执行、授权判断和数据归属使用的业务主体。 */
public interface Principal {

    /**
     * 返回Principal 标识。
     *
     * @return Principal 标识
     */
    long principalId();

}
