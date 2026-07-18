package com.sca4d.mail;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.mustachejava.DefaultMustacheFactory;
import com.github.mustachejava.Mustache;
import com.github.mustachejava.MustacheException;
import com.github.mustachejava.reflect.MapObjectHandler;
import com.sca4d.common.exception.BizException;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.owasp.html.PolicyFactory;
import org.owasp.html.Sanitizers;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.StringReader;
import java.io.StringWriter;
import java.io.Writer;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Pattern;

/** 渲染只含简单变量的 Mustache 邮件模板，并清洗最终 HTML。 */
@Component
public class MailTemplateRenderer {

    private static final Logger log = LoggerFactory.getLogger(MailTemplateRenderer.class);

    private static final int MAX_TEMPLATE_LENGTH = 100_000;
    private static final int MAX_RENDERED_LENGTH = 500_000;
    private static final Pattern VARIABLE_NAME = Pattern.compile("[a-z][a-zA-Z0-9_]{0,63}");
    private static final Set<String> RESERVED_VARIABLE_NAMES = Set.of("class", "getClass");
    private static final PolicyFactory HTML_POLICY = Sanitizers.FORMATTING
            .and(Sanitizers.BLOCKS)
            .and(Sanitizers.LINKS)
            .and(Sanitizers.TABLES);

    private final DefaultMustacheFactory textFactory = factory(false);
    private final DefaultMustacheFactory htmlFactory = factory(true);
    private final Cache<CompiledTemplateKey, Mustache> compiledTemplates = Caffeine.newBuilder()
            .maximumSize(512)
            .build();

    /**
     * 渲染一封邮件。模板只能使用 {@code {{variable}}}，且每个变量都必须由调用方提供。
     *
     * @param htmlTemplate 可选 HTML 模板
     */
    public @NotNull RenderedMail render(
            @NotNull String subjectTemplate,
            @NotNull String textTemplate,
            @Nullable String htmlTemplate,
            @NotNull Map<String, String> variables) {
        Map<String, String> values = Map.copyOf(variables);
        String subject = render(subjectTemplate, values, false);
        if (subject.indexOf('\r') >= 0 || subject.indexOf('\n') >= 0) {
            throw new BizException(MailErrorCode.TEMPLATE_RENDER_INVALID);
        }
        String text = render(textTemplate, values, false);
        String html = htmlTemplate == null
                ? null
                : HTML_POLICY.sanitize(render(htmlTemplate, values, true));
        return new RenderedMail(subject, text, html);
    }

    /**
     * 校验受限 Mustache 语法并返回模板引用的变量名。
     *
     * <p>不允许 section、partial、comment、lambda、反转义变量或自定义分隔符。
     */
    public @NotNull Set<String> variables(@NotNull String template) {
        if (template.length() > MAX_TEMPLATE_LENGTH) {
            throw new BizException(MailErrorCode.TEMPLATE_CONTENT_INVALID);
        }
        LinkedHashSet<String> variables = new LinkedHashSet<>();
        int cursor = 0;
        while (cursor < template.length()) {
            int open = template.indexOf("{{", cursor);
            int unexpectedClose = template.indexOf("}}", cursor);
            if (unexpectedClose >= 0 && (open < 0 || unexpectedClose < open)) {
                throw new BizException(MailErrorCode.TEMPLATE_CONTENT_INVALID);
            }
            if (open < 0) {
                break;
            }
            int close = template.indexOf("}}", open + 2);
            if (close < 0) {
                throw new BizException(MailErrorCode.TEMPLATE_CONTENT_INVALID);
            }
            String name = template.substring(open + 2, close).trim();
            if (!VARIABLE_NAME.matcher(name).matches() || RESERVED_VARIABLE_NAMES.contains(name)) {
                throw new BizException(MailErrorCode.TEMPLATE_CONTENT_INVALID);
            }
            variables.add(name);
            cursor = close + 2;
        }
        return Collections.unmodifiableSet(variables);
    }

    /**
     * 使用受限变量表渲染单段模板，并按模板原文和转义模式复用编译结果。
     *
     * @param template 已通过长度限制的模板原文
     * @param variables 调用方提供的完整变量表
     * @param html 是否启用 Mustache HTML 转义
     * @return 不超过渲染长度上限的结果
     */
    private @NotNull String render(
            @NotNull String template,
            @NotNull Map<String, String> variables,
            boolean html) {
        Set<String> referencedVariables = variables(template);
        for (String name : referencedVariables) {
            if (!variables.containsKey(name)) {
                throw new BizException(MailErrorCode.TEMPLATE_RENDER_INVALID);
            }
        }
        CompiledTemplateKey key = new CompiledTemplateKey(template, html);
        Mustache mustache = compiledTemplates.getIfPresent(key);
        if (mustache == null) {
            mustache = compiledTemplates.get(key, ignored -> compile(template, html));
            log.trace(
                    "邮件模板编译缓存未命中 | templateHash={} html={}",
                    Integer.toUnsignedString(template.hashCode(), 16),
                    html);
        } else {
            log.trace(
                    "邮件模板编译缓存命中 | templateHash={} html={}",
                    Integer.toUnsignedString(template.hashCode(), 16),
                    html);
        }
        StringWriter output = new StringWriter(Math.min(template.length() * 2, MAX_RENDERED_LENGTH));
        try {
            mustache.execute(output, variables).flush();
        } catch (IOException | MustacheException exception) {
            throw new BizException(MailErrorCode.TEMPLATE_RENDER_INVALID, exception);
        }
        if (output.getBuffer().length() > MAX_RENDERED_LENGTH) {
            throw new BizException(MailErrorCode.TEMPLATE_RENDER_INVALID);
        }
        return output.toString();
    }

    /**
     * 使用与输出类型匹配的 MustacheFactory 编译一段模板。
     *
     * @param template 已通过受限语法校验的模板原文
     * @param html 是否启用 Mustache HTML 转义
     * @return 可安全复用的已编译模板
     */
    private @NotNull Mustache compile(@NotNull String template, boolean html) {
        DefaultMustacheFactory factory = html ? htmlFactory : textFactory;
        try {
            return factory.compile(
                    new StringReader(template),
                    "mail-template-" + Integer.toUnsignedString(template.hashCode(), 16));
        } catch (MustacheException exception) {
            throw new BizException(MailErrorCode.TEMPLATE_CONTENT_INVALID, exception);
        }
    }

    /**
     * 创建只允许从变量 Map 读取值的 MustacheFactory。
     *
     * @param escapeHtml 是否使用 Mustache 默认 HTML 转义
     * @return 已安装 MapObjectHandler 的模板工厂
     */
    private static @NotNull DefaultMustacheFactory factory(boolean escapeHtml) {
        DefaultMustacheFactory factory = escapeHtml
                ? new DefaultMustacheFactory()
                : new PlainTextMustacheFactory();
        factory.setObjectHandler(new MapObjectHandler());
        return factory;
    }

    /** 缓存编译结果时区分模板原文与 HTML escaping 模式。 */
    private static final class CompiledTemplateKey {

        private final String template;
        private final boolean html;

        /**
         * 创建缓存键，并把相同模板在文本与 HTML 模式下的编译结果分开。
         *
         * @param template 模板原文
         * @param html 是否启用 HTML 转义
         */
        private CompiledTemplateKey(@NotNull String template, boolean html) {
            this.template = template;
            this.html = html;
        }

        @Override
        public boolean equals(@Nullable Object object) {
            if (this == object) {
                return true;
            }
            if (!(object instanceof CompiledTemplateKey that)) {
                return false;
            }
            return html == that.html && template.equals(that.template);
        }

        @Override
        public int hashCode() {
            return Objects.hash(template, html);
        }
    }

    /** 文本邮件保留变量原文；HTML 邮件继续使用 Mustache 的 HTML escaping。 */
    private static final class PlainTextMustacheFactory extends DefaultMustacheFactory {

        @Override
        public void encode(String value, Writer writer) {
            try {
                writer.write(value);
            } catch (IOException exception) {
                throw new MustacheException("无法写入邮件模板渲染结果", exception);
            }
        }
    }
}
