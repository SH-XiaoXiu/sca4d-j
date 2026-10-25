package com.sca4d.core.spring.dictionary;

import com.sca4d.core.api.dictionary.DictionaryOption;
import com.sca4d.core.api.persistence.AffectedRowCounts;
import com.sca4d.core.api.persistence.Patch;
import org.jetbrains.annotations.CheckReturnValue;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Unmodifiable;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

/** 封装 `DictionaryMapper` 的动态字典与数据库选项读写，并把影响行数转换为 `boolean` 结果。 */
public class DictionaryRepository {

    private final DictionaryMapper mapper;

    /**
     * 创建字典 Repository。
     *
     * @param mapper 动态字典和数据库选项的 MyBatis Mapper，不能为空
     */
    public DictionaryRepository(@NotNull DictionaryMapper mapper) {
        this.mapper = Objects.requireNonNull(mapper, "Dictionary Mapper 不能为空");
    }

    /** @return 全部数据库动态字典 */
    @CheckReturnValue
    public @NotNull @Unmodifiable List<@NotNull DynamicDictionary> findDictionaries() {
        return List.copyOf(mapper.selectDictionaries(null));
    }

    /** @return 指定数据库动态字典；不存在时返回空 */
    @CheckReturnValue
    public @NotNull Optional<DynamicDictionary> findDictionary(@NotNull String key) {
        return mapper.selectDictionaries(Objects.requireNonNull(key, "Dictionary 键不能为空")).stream().findFirst();
    }

    /** @return 指定字典保存的全部数据库选项 */
    @CheckReturnValue
    public @NotNull @Unmodifiable List<@NotNull DatabaseDictionaryOption> findOptions(@NotNull String key) {
        return List.copyOf(mapper.selectOptions(Objects.requireNonNull(key, "Dictionary 键不能为空")));
    }

    /**
     * 新增动态字典。
     *
     * @param key 字典键，同时作为数据库主键
     * @param label 字典名称
     * @param enabled 字典是否启用
     * @param sortOrder 字典排序值
     * @return 新增成功时为 {@code true}；键已经存在时为 {@code false}，原字典不变
     */
    @CheckReturnValue
    public boolean createDictionary(
            @NotNull String key,
            @NotNull String label,
            boolean enabled,
            int sortOrder) {
        return AffectedRowCounts.changed(
                mapper.insertDictionary(
                        Objects.requireNonNull(key, "Dictionary 键不能为空"),
                        Objects.requireNonNull(label, "Dictionary 名称不能为空"),
                        enabled,
                        sortOrder),
                "新增动态字典");
    }

    /**
     * 按乐观版本修改动态字典的名称、启用状态和排序值。
     *
     * @param key 字典键
     * @param label 修改后的字典名称
     * @param enabled 修改后的启用状态
     * @param sortOrder 修改后的排序值
     * @param version 调用方读取到的乐观锁版本
     * @return 修改成功时为 {@code true}；目标不存在或版本变化时为 {@code false}
     */
    @CheckReturnValue
    public boolean updateDictionary(
            @NotNull String key,
            @NotNull String label,
            boolean enabled,
            int sortOrder,
            long version) {
        return AffectedRowCounts.changed(
                mapper.updateDictionary(
                        Objects.requireNonNull(key, "Dictionary 键不能为空"),
                        Patch.set(Objects.requireNonNull(label, "Dictionary 名称不能为空")),
                        Patch.set(enabled),
                        Patch.set(sortOrder),
                        version),
                "修改动态字典");
    }

    /**
     * 删除动态字典及其数据库选项。
     *
     * <p>先按版本删除字典，再删除选项，整体提交；版本冲突返回 {@code false} 且不改动选项。
     */
    @CheckReturnValue
    @Transactional
    public boolean deleteDictionary(@NotNull String key, long version) {
        String checked = Objects.requireNonNull(key, "Dictionary 键不能为空");
        if (!AffectedRowCounts.changed(mapper.deleteDictionary(checked, version), "删除动态字典")) {
            return false;
        }
        mapper.deleteOptions(checked);
        return true;
    }

    /**
     * 新增数据库选项。
     *
     * @param key 动态字典或允许扩展的代码字典键
     * @param option 已校验回退标签和 i18n 的数据库选项
     * @return 新增成功时为 {@code true}；同一字典中值重复时为 {@code false}
     * @throws IllegalArgumentException option 是代码定义项时抛出
     */
    @CheckReturnValue
    public boolean createOption(
            @NotNull String key,
            @NotNull DictionaryOption option) {
        DictionaryOption checked = requireDatabaseOption(option);
        return AffectedRowCounts.changed(
                mapper.insertOption(
                        Objects.requireNonNull(key, "Dictionary 键不能为空"),
                        checked.value(),
                        Objects.requireNonNull(checked.label(), "Dictionary 标签不能为空"),
                        Objects.requireNonNull(checked.i18n(), "Dictionary 多语言标签不能为空"),
                        checked.enabled(),
                        checked.sortOrder()),
                "新增字典选项");
    }

    /**
     * 按乐观版本完整修改数据库选项。
     *
     * @param key 动态字典或允许扩展的代码字典键
     * @param option 已校验回退标签和 i18n 的数据库选项
     * @param version 调用方读取到的乐观锁版本
     * @return 修改成功时为 {@code true}；目标不存在或版本变化时为 {@code false}
     * @throws IllegalArgumentException option 是代码定义项时抛出
     */
    @CheckReturnValue
    public boolean updateOption(
            @NotNull String key,
            @NotNull DictionaryOption option,
            long version) {
        DictionaryOption checked = requireDatabaseOption(option);
        return AffectedRowCounts.changed(
                mapper.updateOption(
                        Objects.requireNonNull(key, "Dictionary 键不能为空"),
                        checked.value(),
                        Patch.set(Objects.requireNonNull(checked.label(), "Dictionary 标签不能为空")),
                        Patch.set(Objects.requireNonNull(checked.i18n(), "Dictionary 多语言标签不能为空")),
                        Patch.set(checked.enabled()),
                        Patch.set(checked.sortOrder()),
                        version),
                "修改字典选项");
    }

    /**
     * 按乐观版本删除一个数据库选项。
     *
     * @param key 字典键
     * @param value 要删除的选项值
     * @param version 调用方读取到的选项乐观锁版本
     * @return 删除成功时为 {@code true}；目标不存在或版本变化时为 {@code false}
     */
    @CheckReturnValue
    public boolean deleteOption(@NotNull String key, @NotNull String value, long version) {
        return AffectedRowCounts.changed(
                mapper.deleteOption(
                        Objects.requireNonNull(key, "Dictionary 键不能为空"),
                        Objects.requireNonNull(value, "Dictionary 值不能为空"),
                        version),
                "删除字典选项");
    }

    /**
     * 检查待写入选项是否来自数据库；代码定义的选项只存在于 Java 枚举中，不能写入数据库。
     *
     * @throws IllegalArgumentException option 的 `codeDefined()` 为 {@code true} 时抛出
     */
    private static @NotNull DictionaryOption requireDatabaseOption(@NotNull DictionaryOption option) {
        DictionaryOption checked = Objects.requireNonNull(option, "Dictionary 选项不能为空");
        if (checked.codeDefined()) {
            throw new IllegalArgumentException("代码 Dictionary 选项不能写入数据库");
        }
        return checked;
    }
}
