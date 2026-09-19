package com.sca4d.iot.config;

import org.springframework.beans.factory.annotation.Qualifier;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/** 限定 IoT TimescaleDB 的基础 Bean；没有该注解的数据库 Bean 仍表示默认数据库。 */
@Documented
@Qualifier
@Target({ElementType.FIELD, ElementType.PARAMETER, ElementType.METHOD, ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
public @interface IotTimescale {

    /** IoT TimescaleDB 写入使用的 Spring 事务管理器 Bean 名称。 */
    String TRANSACTION_MANAGER = "iotTimescaleTransactionManager";
}
