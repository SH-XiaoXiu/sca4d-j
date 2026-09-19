package com.sca4d.core.spring.dictionary;

import com.sca4d.core.api.i18n.LocalizedFields;
import com.sca4d.core.api.persistence.Patch;
import org.apache.ibatis.annotations.Param;
import org.jetbrains.annotations.CheckReturnValue;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/** 定义动态字典和代码字典扩展项的最小查询与写入 SQL。 */
public interface DictionaryMapper {

    /** @return 全部或指定键的数据库动态字典 */
    @CheckReturnValue
    @NotNull
    List<@NotNull DynamicDictionary> selectDictionaries(@Param("key") @Nullable String key);

    /** @return 指定字典保存的全部数据库选项 */
    @CheckReturnValue
    @NotNull
    List<@NotNull DatabaseDictionaryOption> selectOptions(@Param("key") @NotNull String key);

    /** @return 新增动态字典行数；键冲突时为 {@code 0} */
    @CheckReturnValue
    int insertDictionary(
            @Param("key") @NotNull String key,
            @Param("label") @NotNull String label,
            @Param("enabled") boolean enabled,
            @Param("sortOrder") int sortOrder);

    /** @return 按键和版本修改动态字典的行数 */
    @CheckReturnValue
    int updateDictionary(
            @Param("key") @NotNull String key,
            @Param("label") @NotNull Patch<String> label,
            @Param("enabled") @NotNull Patch<Boolean> enabled,
            @Param("sortOrder") @NotNull Patch<Integer> sortOrder,
            @Param("version") long version);

    /** @return 按键和版本删除动态字典的行数 */
    @CheckReturnValue
    int deleteDictionary(@Param("key") @NotNull String key, @Param("version") long version);

    /** 删除指定字典的全部数据库选项。 */
    void deleteOptions(@Param("key") @NotNull String key);

    /**
     * 新增一个数据库字典选项。
     *
     * @param key 字典键
     * @param value 选项稳定值
     * @param label 缺少翻译时使用的回退标签
     * @param i18n 按 Locale 保存的完整多语言 label
     * @param enabled 是否允许用于新的业务输入
     * @param sortOrder 同一字典内的展示顺序
     * @return 新增行数；值冲突时为 {@code 0}
     */
    @CheckReturnValue
    int insertOption(
            @Param("key") @NotNull String key,
            @Param("value") @NotNull String value,
            @Param("label") @NotNull String label,
            @Param("i18n") @NotNull LocalizedFields i18n,
            @Param("enabled") boolean enabled,
            @Param("sortOrder") int sortOrder);

    /**
     * 按字典键、值和版本完整修改数据库选项。
     *
     * @param key 字典键
     * @param value 选项稳定值
     * @param label 缺少翻译时使用的回退标签
     * @param i18n 按 Locale 保存的完整多语言 label
     * @param enabled 是否允许用于新的业务输入
     * @param sortOrder 同一字典内的展示顺序
     * @param version 调用方读取到的乐观锁版本
     * @return 修改行数；目标不存在或版本变化时为 {@code 0}
     */
    @CheckReturnValue
    int updateOption(
            @Param("key") @NotNull String key,
            @Param("value") @NotNull String value,
            @Param("label") @NotNull Patch<String> label,
            @Param("i18n") @NotNull Patch<LocalizedFields> i18n,
            @Param("enabled") @NotNull Patch<Boolean> enabled,
            @Param("sortOrder") @NotNull Patch<Integer> sortOrder,
            @Param("version") long version);

    /** @return 按字典键、值和版本删除数据库选项的行数 */
    @CheckReturnValue
    int deleteOption(
            @Param("key") @NotNull String key,
            @Param("value") @NotNull String value,
            @Param("version") long version);
}
