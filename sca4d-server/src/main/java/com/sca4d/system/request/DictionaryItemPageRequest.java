package com.sca4d.system.request;

import com.sca4d.common.web.PageQuery;
import com.sca4d.system.DictionaryStatus;
import org.jetbrains.annotations.Nullable;

/** 管理端字典项分页、固定值搜索和状态筛选条件。 */
public class DictionaryItemPageRequest extends PageQuery {

    private String search;
    private DictionaryStatus status;

    public @Nullable String getSearch() {
        return search;
    }

    public void setSearch(@Nullable String search) {
        this.search = search;
    }

    public @Nullable DictionaryStatus getStatus() {
        return status;
    }

    public void setStatus(@Nullable DictionaryStatus status) {
        this.status = status;
    }
}
