package com.sca4d.system.request;

import com.sca4d.common.validation.Required;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import lombok.Data;

import java.util.List;

/** 提交当前管理范围内全部字典类型或字典项的最终顺序。 */
@Data
public class DictionaryReorderRequest {

    @NotEmpty
    private List<@Valid Item> items;

    /** 单个排序资源的主键和拖动前版本。 */
    @Data
    public static class Item {

        @Required
        private Long id;

        @Required
        private Long version;
    }
}
