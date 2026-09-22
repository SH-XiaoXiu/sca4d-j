package com.sca4d.iam.base;

import com.sca4d.core.api.query.PageRequest;
import com.sca4d.iam.identity.IdentityStatus;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/** 保存 Platform Account 目录的一页查询条件。 */
@SuppressWarnings("ClassCanBeRecord") // 项目统一使用普通 class，不使用 Java record。
public final class PlatformAccountQuery {

    private final @NotNull PageRequest page;

    private final @Nullable String search;

    private final @Nullable IdentityStatus status;

    /**
     * 创建账号目录查询。
     *
     * @param search 可选的用户名搜索词
     * @param status 可选的账号状态筛选
     * @param page 从 1 开始且每页数量不超过 100 的分页要求
     */
    public PlatformAccountQuery(
            @Nullable String search,
            @Nullable IdentityStatus status,
            @NotNull PageRequest page) {
        String checkedSearch = search == null ? null : search.trim();
        if (checkedSearch != null && checkedSearch.length() > 128) {
            throw new IllegalArgumentException("账号搜索词长度不能超过 128");
        }
        this.page = page.verifyMaxSize(100);
        this.search = checkedSearch == null || checkedSearch.isEmpty() ? null : checkedSearch;
        this.status = status;
    }

    /** @return 从 1 开始且每页数量不超过 100 的分页要求 */
    @Contract(pure = true)
    public @NotNull PageRequest page() {
        return page;
    }

    /** @return 用户名搜索词；未指定时为空 */
    @Contract(pure = true)
    public @Nullable String search() {
        return search;
    }

    /** @return 账号状态筛选；未指定时为空 */
    @Contract(pure = true)
    public @Nullable IdentityStatus status() {
        return status;
    }

    /** @return 当前页第一行相对于完整结果的偏移量 */
    @Contract(pure = true)
    public long offset() {
        return page.offset();
    }
}
