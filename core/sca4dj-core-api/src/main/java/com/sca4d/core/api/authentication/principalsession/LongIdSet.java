package com.sca4d.core.api.authentication.principalsession;

import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;

import java.util.Arrays;
import java.util.Collection;
import java.util.Objects;

/** 保存互不重复的正数 {@code long} 标识，并提供不可变的精确包含判断。 */
public final class LongIdSet {

    private final long @NotNull [] ids;

    /**
     * 复制、排序并校验一组标识。
     *
     * @param ids 可以为空、元素互不重复的正数标识
     * @throws NullPointerException 集合或任一元素为 {@code null} 时抛出
     * @throws IllegalArgumentException 包含非正数或重复标识时抛出
     */
    public LongIdSet(@NotNull Collection<@NotNull Long> ids) {
        Collection<Long> checked = Objects.requireNonNull(ids, "标识集合不能为空");
        this.ids = new long[checked.size()];
        int index = 0;
        for (Long id : checked) {
            long checkedId = Objects.requireNonNull(id, "标识不能为空");
            if (checkedId <= 0) {
                throw new IllegalArgumentException("标识必须为正数: " + checkedId);
            }
            this.ids[index++] = checkedId;
        }
        Arrays.sort(this.ids);
        for (int current = 1; current < this.ids.length; current++) {
            if (this.ids[current - 1] == this.ids[current]) {
                throw new IllegalArgumentException("标识集合包含重复值: " + this.ids[current]);
            }
        }
    }

    /**
     * 判断集合是否包含指定标识。
     *
     * @param id 待查找的标识；非正数始终不匹配
     * @return 集合包含指定标识时返回 {@code true}
     */
    @Contract(pure = true)
    public boolean contains(long id) {
        return id > 0 && Arrays.binarySearch(ids, id) >= 0;
    }

    /**
     * 返回集合中的标识数量。
     *
     * @return 标识数量
     */
    @Contract(pure = true)
    public int size() {
        return ids.length;
    }

    /**
     * 判断集合是否没有标识。
     *
     * @return 集合为空时返回 {@code true}
     */
    @Contract(pure = true)
    public boolean isEmpty() {
        return ids.length == 0;
    }

    /**
     * 按从小到大的顺序复制全部标识。
     *
     * @return 不与当前集合共享存储的标识数组
     */
    @Contract(pure = true)
    public long @NotNull [] toArray() {
        return Arrays.copyOf(ids, ids.length);
    }
}
