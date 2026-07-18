package com.sca4d.system.request;

import com.sca4d.common.concurrent.VersionedRequest;
import com.sca4d.common.i18n.SupportedLanguage;
import com.sca4d.common.validation.BizId;
import com.sca4d.common.validation.BizIdMode;
import com.sca4d.common.validation.Required;
import com.sca4d.system.MenuIconName;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.Map;

/** 修改系统菜单父关系、代码路由引用和展示数据的请求。 */
@Data
public class MenuUpdateRequest implements VersionedRequest {

    private Long parentId;

    @BizId(mode = BizIdMode.NAMESPACED_CODE, max = 128)
    private String routeCode;

    @NotEmpty
    private Map<@SupportedLanguage String, @Size(min = 1, max = 128) String> titles;

    @Required
    private MenuIconName icon;

    @Required
    private Boolean visible;

    @Required
    private Long version;
}
