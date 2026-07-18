package com.sca4d.system.request;

import com.sca4d.common.web.PageQuery;
import com.sca4d.system.DictionaryKind;
import com.sca4d.system.DictionaryStatus;
import org.jetbrains.annotations.Nullable;

/** 管理端字典类型分页、搜索和有限状态筛选条件。 */
public class DictionaryPageRequest extends PageQuery {

    private String search;
    private DictionaryKind kind;
    private DictionaryStatus status;

    public @Nullable String getSearch() {
        return search;
    }

    public void setSearch(@Nullable String search) {
        this.search = search;
    }

    public @Nullable DictionaryKind getKind() {
        return kind;
    }

    public void setKind(@Nullable DictionaryKind kind) {
        this.kind = kind;
    }

    public @Nullable DictionaryStatus getStatus() {
        return status;
    }

    public void setStatus(@Nullable DictionaryStatus status) {
        this.status = status;
    }
}
