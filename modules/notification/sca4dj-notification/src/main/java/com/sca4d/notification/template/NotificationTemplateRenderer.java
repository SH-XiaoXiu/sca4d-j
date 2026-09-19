package com.sca4d.notification.template;

import com.fasterxml.jackson.databind.JsonNode;
import org.jetbrains.annotations.CheckReturnValue;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;

import java.util.Map;

/** 把一个 Locale 的标题和正文文档转换成渠道可以使用的字符串。 */
public interface NotificationTemplateRenderer {

    /** @return 具体应用和渠道选择当前实现时使用的稳定编码 */
    @CheckReturnValue
    @Contract(pure = true)
    @NotNull
    String code();

    /**
     * 渲染已经完成公共结构校验的标题和正文。
     *
     * @param title ProseMirror 标题文档
     * @param body ProseMirror 正文文档
     * @param variables 当前通知提供的完整变量
     * @return 当前格式的标题和正文
     */
    @NotNull
    @CheckReturnValue
    @Contract(pure = true)
    RenderedTemplate render(
            @NotNull JsonNode title,
            @NotNull JsonNode body,
            @NotNull Map<@NotNull String, @NotNull String> variables);
}
