package com.sca4d.common.dictionary;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 补充代码字典枚举项的展示元数据。
 *
 * <p>未声明本注解时，标签消息键按字典编码和枚举固定值生成。闭集字典的标签键、色调和可选择状态由代码维护；可扩展字典只用
 * 这些值初始化新保留项，后续展示属性由管理端维护。排序始终由数据库维护，枚举声明顺序只用于首次写入。
 */
@Documented
@Target(ElementType.FIELD)
@Retention(RetentionPolicy.RUNTIME)
public @interface CodeDictionaryItem {

    /**
     * 枚举项标签消息键。
     *
     * @return 显式消息键；空白表示使用目录约定生成
     */
    String labelKey() default "";

    /**
     * 含义展示色调。
     *
     * @return 共用字典色调
     */
    DictionaryTone tone() default DictionaryTone.DEFAULT;

    /**
     * 是否允许用于新的输入。
     *
     * @return {@code false} 表示该值只能读取和展示，不能再用于新写入
     */
    boolean selectable() default true;

    /**
     * 是否把当前枚举常量发布为字典项。
     *
     * <p>未发布常量仍参与固定值格式和唯一性校验，但不要求国际化文案、不写入数据库，也不由共用选项接口返回。
     *
     * @return {@code true} 表示形成字典投影
     */
    boolean published() default true;
}
