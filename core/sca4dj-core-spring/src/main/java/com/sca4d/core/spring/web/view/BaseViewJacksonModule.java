package com.sca4d.core.spring.web.view;

import com.fasterxml.jackson.databind.module.SimpleModule;
import com.sca4d.core.api.i18n.TextLocalizer;
import org.jetbrains.annotations.NotNull;

/** 创建只处理 BaseView 子类的 Jackson 模块，供 MVC 实际 converter 安装。 */
public class BaseViewJacksonModule extends SimpleModule {

    /**
     * 创建只包装 BaseView 子类序列化器的 Jackson 模块。
     *
     * @param localizer 应用文本本地化入口
     */
    public BaseViewJacksonModule(@NotNull TextLocalizer localizer) {
        super("sca4dj-base-view");
        setSerializerModifier(new BaseViewSerializerModifier(localizer));
    }
}
