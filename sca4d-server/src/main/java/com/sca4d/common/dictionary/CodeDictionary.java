package com.sca4d.common.dictionary;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 声明由 Java 枚举定义并向共用选项接口发布的代码字典。
 *
 * <p>被标注枚举必须实现 {@code BaseEnum}。闭集字典由代码完整定义；可扩展字典中的枚举只声明操作保留值，数据库可以同时保存
 * 普通业务编码。未声明本注解的 {@code BaseEnum} 不进入数据库投影，也不向前端发布。
 */
@Documented
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
public @interface CodeDictionary {

    /**
     * 字典类型编码。
     *
     * @return 全局唯一的点分小写编码
     */
    String code();

    /**
     * 字典名称消息键。
     *
     * @return 后端国际化资源中的固定消息键
     */
    String nameKey();

    /**
     * 字典是否允许数据库维护枚举之外的普通业务编码。
     *
     * @return 闭集或可扩展代码字典模式
     */
    CodeDictionaryMode mode() default CodeDictionaryMode.CLOSED;
}
