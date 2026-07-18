package com.sca4d.mail.view;

import com.sca4d.mail.RenderedMail;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.jetbrains.annotations.NotNull;

/** 管理端预览使用的已渲染邮件主题、文本和安全 HTML。 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class RenderedMailView {

    private String subject;
    private String text;
    private String html;

    /** @return 可以直接显示在隔离预览区域的渲染结果 */
    public static @NotNull RenderedMailView from(@NotNull RenderedMail mail) {
        return new RenderedMailView(mail.getSubject(), mail.getText(), mail.getHtml());
    }
}
