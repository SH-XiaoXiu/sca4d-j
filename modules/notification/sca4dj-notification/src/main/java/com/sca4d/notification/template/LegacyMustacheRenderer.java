package com.sca4d.notification.template;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.mustachejava.DefaultMustacheFactory;
import com.github.mustachejava.DefaultMustacheVisitor;
import com.github.mustachejava.Mustache;
import com.github.mustachejava.MustacheException;
import com.github.mustachejava.MustacheVisitor;
import com.github.mustachejava.TemplateContext;
import com.github.mustachejava.reflect.MapObjectHandler;

import java.io.IOException;
import java.io.StringReader;
import java.io.StringWriter;
import java.io.Writer;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Pattern;

/** 解析 V7 迁移节点中的旧 Mustache 文本，不作为通知模板扩展接口。 */
final class LegacyMustacheRenderer {

    static final int MAXIMUM_SOURCE_LENGTH = 100_000;

    private static final int MAXIMUM_RENDERED_LENGTH = 500_000;

    private static final Pattern VARIABLE = Pattern.compile("[a-z][a-zA-Z0-9_]{0,63}");

    private static final Set<String> RESERVED_VARIABLES = Set.of("class", "getClass");

    private final Cache<TemplateCacheKey, CompiledExpression> templates = Caffeine.newBuilder()
            .maximumSize(512)
            .build();

    /** @return 旧模板原文引用的简单变量 */
    Set<String> variables(String source) {
        return compiled(source, OutputFormat.TEXT).variables;
    }

    /** 使用原始变量文本渲染旧纯文本节点。 */
    String renderText(String source, Map<String, String> variables) {
        return render(source, variables, OutputFormat.TEXT);
    }

    /** 使用 HTML escaping 渲染随后仍会经过 allowlist 清洗的旧 HTML 节点。 */
    String renderHtml(String source, Map<String, String> variables) {
        return render(source, variables, OutputFormat.HTML);
    }

    private String render(String source, Map<String, String> variables, OutputFormat outputFormat) {
        CompiledExpression compiled = compiled(source, outputFormat);
        Map<String, String> values = Map.copyOf(Objects.requireNonNull(variables, "通知模板变量不能为空"));
        for (String variable : compiled.variables) {
            if (!values.containsKey(variable)) {
                throw new IllegalArgumentException("通知模板缺少变量: " + variable);
            }
        }
        StringWriter output = new StringWriter(Math.min(source.length() * 2, MAXIMUM_RENDERED_LENGTH));
        try {
            compiled.mustache.execute(output, values).flush();
        } catch (IOException | MustacheException exception) {
            throw new IllegalArgumentException("通知模板渲染失败", exception);
        }
        if (output.getBuffer().length() > MAXIMUM_RENDERED_LENGTH) {
            throw new IllegalArgumentException("通知模板渲染结果不能超过 " + MAXIMUM_RENDERED_LENGTH + " 个字符");
        }
        return output.toString();
    }

    /** 按模板原文和输出格式读取或编译表达式。 */
    private CompiledExpression compiled(String source, OutputFormat outputFormat) {
        String checkedSource = Objects.requireNonNull(source, "通知模板原文不能为空");
        if (checkedSource.length() > MAXIMUM_SOURCE_LENGTH) {
            throw new IllegalArgumentException("通知模板原文不能超过 " + MAXIMUM_SOURCE_LENGTH + " 个字符");
        }
        OutputFormat checkedFormat = Objects.requireNonNull(outputFormat, "通知模板输出格式不能为空");
        TemplateCacheKey key = new TemplateCacheKey(checkedSource, checkedFormat);
        return templates.get(key, ignored -> compile(checkedSource, checkedFormat));
    }

    /** 使用拒绝控制结构的 Visitor 编译一段旧模板原文。 */
    private CompiledExpression compile(String source, OutputFormat outputFormat) {
        RestrictedMustacheFactory factory = new RestrictedMustacheFactory(outputFormat);
        try {
            Mustache mustache = factory.compile(
                    new StringReader(source),
                    "notification-template-" + Integer.toUnsignedString(source.hashCode(), 16));
            return new CompiledExpression(mustache, factory.variables());
        } catch (MustacheException exception) {
            throw new IllegalArgumentException("通知模板表达式格式不正确", exception);
        }
    }

    /** 区分旧模板变量使用纯文本原文还是 HTML escaping。 */
    private enum OutputFormat {
        TEXT,
        HTML
    }

    /** 保存一项缓存编译结果。 */
    private static final class CompiledExpression {

        private final Mustache mustache;

        private final Set<String> variables;

        private CompiledExpression(Mustache mustache, Set<String> variables) {
            this.mustache = Objects.requireNonNull(mustache, "Mustache 编译结果不能为空");
            this.variables = Set.copyOf(variables);
        }
    }

    /** 区分相同原文在纯文本和 HTML 模式下的编译结果。 */
    private static final class TemplateCacheKey {

        private final String source;

        private final OutputFormat outputFormat;

        private TemplateCacheKey(String source, OutputFormat outputFormat) {
            this.source = Objects.requireNonNull(source, "模板缓存原文不能为空");
            this.outputFormat = Objects.requireNonNull(outputFormat, "模板缓存输出格式不能为空");
        }

        @Override
        public boolean equals(Object other) {
            return this == other
                    || other instanceof TemplateCacheKey key
                            && source.equals(key.source)
                            && outputFormat == key.outputFormat;
        }

        @Override
        public int hashCode() {
            return Objects.hash(source, outputFormat);
        }
    }

    /** 创建只允许 Map 变量并按输出格式转义的 MustacheFactory。 */
    private static final class RestrictedMustacheFactory extends DefaultMustacheFactory {

        private final OutputFormat outputFormat;

        private RestrictedMustacheVisitor visitor;

        private RestrictedMustacheFactory(OutputFormat outputFormat) {
            this.outputFormat = Objects.requireNonNull(outputFormat, "Mustache 输出格式不能为空");
            setObjectHandler(new MapObjectHandler());
        }

        @Override
        public MustacheVisitor createMustacheVisitor() {
            RestrictedMustacheVisitor created = new RestrictedMustacheVisitor(this);
            visitor = created;
            return created;
        }

        @Override
        public void encode(String value, Writer writer) {
            if (outputFormat == OutputFormat.HTML) {
                super.encode(value, writer);
                return;
            }
            try {
                writer.write(value);
            } catch (IOException exception) {
                throw new MustacheException("无法写入通知模板渲染结果", exception);
            }
        }

        private Set<String> variables() {
            if (visitor == null) {
                throw new IllegalStateException("通知模板尚未解析");
            }
            return visitor.variables();
        }
    }

    /** 只接受普通文本和已转义简单变量的 Mustache Visitor。 */
    private static final class RestrictedMustacheVisitor extends DefaultMustacheVisitor {

        private final LinkedHashSet<String> variables = new LinkedHashSet<>();

        private RestrictedMustacheVisitor(DefaultMustacheFactory factory) {
            super(factory);
        }

        @Override
        public void value(TemplateContext context, String name, boolean encoded) {
            String checked = name == null ? "" : name.trim();
            if (!encoded || !VARIABLE.matcher(checked).matches() || RESERVED_VARIABLES.contains(checked)) {
                throw unsupported();
            }
            variables.add(checked);
            super.value(context, checked, true);
        }

        @Override
        public void iterable(TemplateContext context, String variable, Mustache mustache) {
            throw unsupported();
        }

        @Override
        public void notIterable(TemplateContext context, String variable, Mustache mustache) {
            throw unsupported();
        }

        @Override
        public void partial(TemplateContext context, String variable, String indent) {
            throw unsupported();
        }

        @Override
        public void dynamicPartial(TemplateContext context, String variable, String indent) {
            throw unsupported();
        }

        @Override
        public void pragma(TemplateContext context, String pragma, String args) {
            throw unsupported();
        }

        @Override
        public void extend(TemplateContext context, String variable, Mustache mustache) {
            throw unsupported();
        }

        @Override
        public void name(TemplateContext context, String variable, Mustache mustache) {
            throw unsupported();
        }

        @Override
        public void checkName(TemplateContext context, String variable, Mustache mustache) {
            throw unsupported();
        }

        @Override
        public void comment(TemplateContext context, String comment) {
            throw unsupported();
        }

        private Set<String> variables() {
            return Set.copyOf(variables);
        }

        private static MustacheException unsupported() {
            return new MustacheException("通知模板只允许普通文本和 {{variable}}");
        }
    }
}
