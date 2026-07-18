package com.sca4d.system.request;

import com.sca4d.common.validation.Required;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import lombok.Data;

import java.util.List;

/** 提交一个父节点下全部直接子菜单的最终顺序。 */
@Data
public class MenuReorderRequest {

    private Long parentId;

    @NotEmpty
    private List<@Valid Item> items;

    /** 单个兄弟菜单的主键和拖动前版本。 */
    @Data
    public static class Item {

        @Required
        private Long menuId;

        @Required
        private Long version;
    }
}
