package com.sca4d.core.spring.json;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.jetbrains.annotations.ApiStatus;

/** 脚手架持久化、Session 和内部消息使用的映射器；业务和客户端协议使用各自的 ObjectMapper。 */
@ApiStatus.Internal
public class Sca4djMapper extends ObjectMapper {

    private static final long serialVersionUID = 1L;

    /** 安装内部稳定的时间和严格输入规则，不继承 HTTP 或业务 Mapper 的定制。 */
    public Sca4djMapper() {
        registerModule(new JavaTimeModule());
        registerModule(new NameJacksonModule());
        disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        enable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);
    }

    /** 复制内部配置和已安装模块，供需要独立读写配置的框架实现使用。 */
    @Override
    public Sca4djMapper copy() {
        return new Sca4djMapper(this);
    }

    /** 由 Jackson 复制已经固定的内部配置。 */
    private Sca4djMapper(Sca4djMapper source) {
        super(source);
    }
}
