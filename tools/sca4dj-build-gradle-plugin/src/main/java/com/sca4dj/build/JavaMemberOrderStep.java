package com.sca4dj.build;

import com.diffplug.spotless.FormatterStep;
import com.sun.source.tree.AnnotatedTypeTree;
import com.sun.source.tree.ArrayTypeTree;
import com.sun.source.tree.AssignmentTree;
import com.sun.source.tree.ClassTree;
import com.sun.source.tree.IdentifierTree;
import com.sun.source.tree.LiteralTree;
import com.sun.source.tree.MemberSelectTree;
import com.sun.source.tree.MethodTree;
import com.sun.source.tree.ParameterizedTypeTree;
import com.sun.source.tree.Tree;
import com.sun.source.tree.UnaryTree;
import com.sun.source.tree.VariableTree;
import com.sun.source.util.JavacTask;
import com.sun.source.util.SourcePositions;
import com.sun.source.util.TreeScanner;
import com.sun.source.util.Trees;

import java.io.IOException;
import java.io.Serializable;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import javax.lang.model.element.Modifier;
import javax.tools.Diagnostic;
import javax.tools.DiagnosticCollector;
import javax.tools.JavaCompiler;
import javax.tools.JavaFileObject;
import javax.tools.SimpleJavaFileObject;
import javax.tools.StandardJavaFileManager;
import javax.tools.ToolProvider;

/** 按类型后缀排列构造注入字段，并安全整理字面量常量。 */
final class JavaMemberOrderStep {
    private static final List<String> DEFAULT_GROUPS = List.of(
            "Service|Services",
            "Repository|Repositories",
            "Mapper",
            "Policy|Validator",
            "Reader|Writer|Calculator",
            "Client|Publisher",
            "Factory|Provider|Registry",
            "Properties");

    private static final Set<String> LOMBOK_CONSTRUCTORS = Set.of(
            "RequiredArgsConstructor",
            "AllArgsConstructor",
            "Value",
            "Data");

    private JavaMemberOrderStep() {
    }

    static List<String> defaultGroups() {
        return DEFAULT_GROUPS;
    }

    /** 创建按当前项目优先级排序的 Spotless 步骤。 */
    static FormatterStep create(List<String> groups) {
        State state = new State(groups);
        return FormatterStep.create("java-member-order", state, configured -> {
            if (configured.version != 1) {
                throw new IllegalStateException("未知的 Java 字段排序规则版本: " + configured.version);
            }
            return source -> format(source, configured.groups);
        });
    }

    static String format(String source, List<String> groups) {
        List<List<String>> suffixes = suffixes(groups);
        JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
        if (compiler == null) {
            throw new AssertionError("Java 字段排序需要 JDK 21");
        }
        DiagnosticCollector<JavaFileObject> diagnostics = new DiagnosticCollector<>();
        JavaFileObject input = new SimpleJavaFileObject(
                URI.create("string:///MemberOrder.java"),
                JavaFileObject.Kind.SOURCE) {
            @Override
            public CharSequence getCharContent(boolean ignoreEncodingErrors) {
                return source;
            }
        };
        try (StandardJavaFileManager files = compiler.getStandardFileManager(
                diagnostics,
                null,
                StandardCharsets.UTF_8)) {
            JavacTask task = (JavacTask) compiler.getTask(
                    null,
                    files,
                    diagnostics,
                    List.of("-proc:none", "-source", "21"),
                    null,
                    List.of(input));
            var units = task.parse().iterator();
            if (!units.hasNext()
                    || diagnostics.getDiagnostics()
                            .stream()
                            .anyMatch(diagnostic -> diagnostic.getKind() == Diagnostic.Kind.ERROR)) {
                throw new AssertionError("无法解析 Java 源码: " + diagnostics.getDiagnostics());
            }
            var unit = units.next();
            SourcePositions positions = Trees.instance(task).getSourcePositions();
            List<Replacement> replacements = new ArrayList<>();
            new TreeScanner<Void, Void>() {
                @Override
                public Void visitClass(ClassTree declaration, Void ignored) {
                    if (declaration.getKind() == Tree.Kind.CLASS) {
                        sortClass(source, unit, positions, declaration, suffixes, replacements);
                    }
                    return super.visitClass(declaration, ignored);
                }
            }.scan(unit, null);
            replacements.sort(Comparator.comparingInt((Replacement replacement) -> replacement.start).reversed());
            StringBuilder result = new StringBuilder(source);
            for (Replacement replacement : replacements) {
                result.replace(replacement.start, replacement.end, replacement.text);
            }
            return result.toString();
        } catch (IOException exception) {
            throw new AssertionError("无法读取 Java 源码", exception);
        }
    }

    private static List<List<String>> suffixes(List<String> groups) {
        List<List<String>> result = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        for (String group : groups) {
            List<String> members = new ArrayList<>();
            for (String suffix : group.split("\\|", -1)) {
                if (suffix.isEmpty()
                        || !Character.isJavaIdentifierStart(suffix.charAt(0))
                        || !suffix.chars().allMatch(Character::isJavaIdentifierPart)
                        || !seen.add(suffix)) {
                    throw new IllegalArgumentException("无效或重复的 Java 字段类型后缀: " + suffix);
                }
                members.add(suffix);
            }
            result.add(List.copyOf(members));
        }
        return result;
    }

    private static void sortClass(
            String source,
            com.sun.source.tree.CompilationUnitTree unit,
            SourcePositions positions,
            ClassTree declaration,
            List<List<String>> suffixes,
            List<Replacement> replacements) {
        List<? extends Tree> members = declaration.getMembers();
        boolean lombokConstructor = declaration.getModifiers()
                .getAnnotations()
                .stream()
                .map(annotation -> annotation.getAnnotationType().toString())
                .map(name -> name.substring(name.lastIndexOf('.') + 1))
                .anyMatch(LOMBOK_CONSTRUCTORS::contains);
        int start = 0;
        while (start < members.size()) {
            Kind kind = kind(members.get(start), declaration, lombokConstructor);
            if (kind == Kind.OTHER) {
                start++;
                continue;
            }
            int end = start + 1;
            while (end < members.size() && kind(members.get(end), declaration, lombokConstructor) == kind) {
                end++;
            }
            if (end - start > 1) {
                List<FieldSpan> spans = new ArrayList<>();
                for (int index = start; index < end; index++) {
                    FieldSpan span = span(source, unit, positions, (VariableTree) members.get(index));
                    if (span == null) {
                        spans.clear();
                        break;
                    }
                    spans.add(span);
                }
                int segment = 0;
                for (int index = 1; index <= spans.size(); index++) {
                    if (index == spans.size()
                            || !separatedOnlyByWhitespace(source, spans.get(index - 1), spans.get(index))) {
                        if (index - segment > 1) {
                            List<FieldSpan> block = spans.subList(segment, index);
                            List<FieldSpan> ordered = kind == Kind.CONSTANT
                                    ? sortConstants(block)
                                    : sortDependencies(block, suffixes);
                            if (!ordered.equals(block)) {
                                String text = ordered.stream()
                                        .map(span -> span.text)
                                        .reduce((left, right) -> left + "\n\n" + right)
                                        .orElseThrow();
                                replacements.add(new Replacement(block.getFirst().start, block.getLast().end, text));
                            }
                        }
                        segment = index;
                    }
                }
            }
            start = end;
        }
    }

    private static Kind kind(Tree member, ClassTree owner, boolean lombokConstructor) {
        if (!(member instanceof VariableTree field)) {
            return Kind.OTHER;
        }
        Set<Modifier> flags = field.getModifiers().getFlags();
        if (flags.contains(Modifier.STATIC) && flags.contains(Modifier.FINAL) && field.getInitializer() != null) {
            return Kind.CONSTANT;
        }
        if (!lombokConstructor
                && flags.contains(Modifier.PRIVATE)
                && flags.contains(Modifier.FINAL)
                && !flags.contains(Modifier.STATIC)
                && field.getInitializer() == null
                && assignedByConstructor(field.getName().toString(), owner)) {
            return Kind.DEPENDENCY;
        }
        return Kind.OTHER;
    }

    private static boolean assignedByConstructor(String name, ClassTree owner) {
        for (Tree member : owner.getMembers()) {
            if (member instanceof MethodTree method && method.getReturnType() == null && method.getBody() != null) {
                Set<String> parameters = new HashSet<>();
                method.getParameters().forEach(parameter -> parameters.add(parameter.getName().toString()));
                if (Boolean.TRUE.equals(new TreeScanner<Boolean, Void>() {
                    @Override
                    public Boolean visitAssignment(AssignmentTree assignment, Void ignored) {
                        if (assignment.getVariable() instanceof MemberSelectTree target
                                && target.getExpression().toString().equals("this")
                                && target.getIdentifier().contentEquals(name)
                                && referencesParameter(assignment.getExpression(), parameters)) {
                            return true;
                        }
                        return super.visitAssignment(assignment, ignored);
                    }

                    @Override
                    public Boolean reduce(Boolean first, Boolean second) {
                        return Boolean.TRUE.equals(first) || Boolean.TRUE.equals(second);
                    }
                }.scan(method.getBody(), null))) {
                    return true;
                }
            }
        }
        return false;
    }

    private static boolean referencesParameter(Tree expression, Set<String> parameters) {
        return Boolean.TRUE.equals(new TreeScanner<Boolean, Void>() {
            @Override
            public Boolean visitIdentifier(IdentifierTree identifier, Void ignored) {
                return parameters.contains(identifier.getName().toString());
            }

            @Override
            public Boolean reduce(Boolean first, Boolean second) {
                return Boolean.TRUE.equals(first) || Boolean.TRUE.equals(second);
            }
        }.scan(expression, null));
    }

    private static FieldSpan span(
            String source,
            com.sun.source.tree.CompilationUnitTree unit,
            SourcePositions positions,
            VariableTree field) {
        long position = positions.getStartPosition(unit, field);
        long ending = positions.getEndPosition(unit, field);
        if (position < 0 || ending < position || ending > source.length()) {
            return null;
        }
        int start = source.lastIndexOf('\n', (int) position - 1) + 1;
        if (!source.substring(start, (int) position).isBlank()) {
            return null;
        }
        start = attachedCommentStart(source, start);
        int newline = source.indexOf('\n', (int) ending);
        int end = newline < 0 ? source.length() : newline;
        String trailing = source.substring((int) ending, end).trim();
        if (!trailing.isEmpty() && !trailing.startsWith("//")) {
            return null;
        }
        return new FieldSpan(field, start, end, source.substring(start, end));
    }

    private static int attachedCommentStart(String source, int start) {
        while (start > 0) {
            int previousEnd = start - 1;
            int previousStart = source.lastIndexOf('\n', previousEnd - 1) + 1;
            String line = source.substring(previousStart, previousEnd).trim();
            if (line.startsWith("//")) {
                start = previousStart;
            } else if (line.endsWith("*/")) {
                int blockStart = previousStart;
                while (!source.substring(blockStart, source.indexOf('\n', blockStart)).contains("/*")) {
                    if (blockStart == 0) {
                        return start;
                    }
                    blockStart = source.lastIndexOf('\n', blockStart - 2) + 1;
                }
                start = blockStart;
            } else {
                break;
            }
        }
        return start;
    }

    private static boolean separatedOnlyByWhitespace(String source, FieldSpan previous, FieldSpan next) {
        return next.start > previous.end && source.substring(previous.end, next.start).isBlank();
    }

    private static List<FieldSpan> sortDependencies(List<FieldSpan> spans, List<List<String>> suffixes) {
        List<FieldSpan> ordered = new ArrayList<>(spans);
        ordered.sort(
                Comparator.comparingInt((FieldSpan span) -> priority(typeName(span.field.getType()), suffixes))
                        .thenComparing(span -> typeName(span.field.getType()))
                        .thenComparing(span -> span.field.getName().toString()));
        return ordered;
    }

    private static int priority(String type, List<List<String>> groups) {
        for (int index = 0; index < groups.size(); index++) {
            if (groups.get(index).stream().anyMatch(type::endsWith)) {
                return index;
            }
        }
        return groups.size();
    }

    private static String typeName(Tree type) {
        if (type instanceof AnnotatedTypeTree annotated)
            return typeName(annotated.getUnderlyingType());
        if (type instanceof ParameterizedTypeTree parameterized)
            return typeName(parameterized.getType());
        if (type instanceof ArrayTypeTree array)
            return typeName(array.getType());
        if (type instanceof MemberSelectTree selected)
            return selected.getIdentifier().toString();
        if (type instanceof IdentifierTree identifier)
            return identifier.getName().toString();
        return type.toString();
    }

    private static List<FieldSpan> sortConstants(List<FieldSpan> spans) {
        List<FieldSpan> literals = spans.stream()
                .filter(span -> literal(span.field.getInitializer()))
                .sorted(Comparator.comparing(span -> span.field.getName().toString()))
                .toList();
        List<FieldSpan> runtime = spans.stream().filter(span -> !literal(span.field.getInitializer())).toList();
        List<FieldSpan> ordered = new ArrayList<>(spans.size());
        if (!runtime.isEmpty() && runtime.getFirst().field.getName().contentEquals("LOG")) {
            ordered.add(runtime.getFirst());
            ordered.addAll(literals);
            ordered.addAll(runtime.subList(1, runtime.size()));
        } else {
            ordered.addAll(literals);
            ordered.addAll(runtime);
        }
        return ordered;
    }

    private static boolean literal(Tree expression) {
        if (expression instanceof LiteralTree value)
            return value.getValue() != null;
        return expression instanceof UnaryTree unary
                && unary.getKind() == Tree.Kind.UNARY_MINUS
                && unary.getExpression() instanceof LiteralTree;
    }

    /** 字段在排序中的类别：常量、依赖注入字段或其他字段。 */
    private enum Kind {
        CONSTANT,
        DEPENDENCY,
        OTHER
    }

    /** 可序列化的排序状态，保存结构版本号和分组列表，构造时用分组列表计算后缀并校验。 */
    private static final class State implements Serializable {
        private static final long serialVersionUID = 1L;

        private final int version;

        private final List<String> groups;

        private State(List<String> groups) {
            this.version = 1;
            this.groups = List.copyOf(groups);
            suffixes(groups);
        }
    }

    /** 源码中一个字段声明的文本范围，start 和 end 为字符偏移量，用于替换整段字段文本。 */
    private static final class FieldSpan {
        private final String text;

        private final VariableTree field;

        private final int end;

        private final int start;

        private FieldSpan(VariableTree field, int start, int end, String text) {
            this.field = field;
            this.start = start;
            this.end = end;
            this.text = text;
        }
    }

    /** 待写回源码的一段文本替换，start 和 end 为被替换范围的字符偏移量。 */
    private static final class Replacement {
        private final String text;

        private final int end;

        private final int start;

        private Replacement(int start, int end, String text) {
            this.start = start;
            this.end = end;
            this.text = text;
        }
    }
}
