package com.sca4dj.gradle.plugin.processor;

import java.io.File;
import java.io.IOException;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Properties;
import java.util.Set;
import java.util.TreeSet;
import java.util.jar.JarFile;
import java.util.stream.Collectors;

import javax.annotation.processing.AbstractProcessor;
import javax.annotation.processing.RoundEnvironment;
import javax.annotation.processing.SupportedAnnotationTypes;
import javax.annotation.processing.SupportedOptions;
import javax.annotation.processing.SupportedSourceVersion;
import javax.lang.model.SourceVersion;
import javax.lang.model.element.AnnotationMirror;
import javax.lang.model.element.Element;
import javax.lang.model.element.ElementKind;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.Modifier;
import javax.lang.model.element.TypeElement;
import javax.lang.model.type.ArrayType;
import javax.lang.model.type.DeclaredType;
import javax.lang.model.type.TypeKind;
import javax.lang.model.type.TypeMirror;
import javax.tools.Diagnostic;
import javax.tools.StandardLocation;

/** 从 App 声明生成类型安全调用与直接分发代码，并输出 API 导出清单。 */
@SupportedAnnotationTypes("com.sca4d.core.api.plugin.*")
@SupportedSourceVersion(SourceVersion.RELEASE_21)
@SupportedOptions({"sca4dj.plugin.api.id", "sca4dj.plugin.api.version",
        "sca4dj.plugin.api.package", "sca4dj.plugin.api.entry", "sca4dj.plugin.api.shared",
        "sca4dj.plugin.api.configuration"})
public final class PluginApiProcessor extends AbstractProcessor {
    private static final String API = "com.sca4d.core.api.plugin.";

    private final Map<String, Contract> contracts = new LinkedHashMap<>();

    private final Set<String> exported = new TreeSet<>();

    private final Set<String> codes = new HashSet<>();

    private final Set<String> validatedTypes = new HashSet<>();

    private final Set<String> sharedClasses = new HashSet<>();

    private boolean sharedLoaded;

    private boolean entryGenerated;

    private boolean metadataGenerated;

    /** 收集声明；生成器不依赖 App 或 Core 实现类。 */
    @Override
    public boolean process(Set<? extends TypeElement> annotations, RoundEnvironment round) {
        // 最后一轮只写描述文件，避免把尚未生成完成的契约写入导出清单。
        if (round.processingOver()) {
            if (!metadataGenerated && !contracts.isEmpty() && !round.errorRaised()) {
                try {
                    writeMetadata();
                    metadataGenerated = true;
                } catch (IOException failure) {
                    error(null, "插件契约描述生成失败: " + failure.getMessage());
                }
            }
            return false;
        }
        // 先校验显式数据类型，递归检查字段和公开方法引用的类型来源。
        for (Element element : annotated(round, "PluginApiType")) {
            if (!(element instanceof TypeElement type) || !type.getModifiers().contains(Modifier.PUBLIC)) {
                error(element, "公开 API 类型必须是 public 类型");
            } else {
                exported.add(type.getQualifiedName().toString());
                try {
                    validateType(type.asType());
                } catch (IllegalArgumentException failure) {
                    error(type, failure.getMessage());
                }
            }
        }
        // 服务和扩展使用同一种契约生成流程，重复轮次不再次生成同名文件。
        List<Element> declarations = new ArrayList<>(annotated(round, "PluginService"));
        declarations.addAll(annotated(round, "PluginExtension"));
        for (Element element : declarations) {
            if (!(element instanceof TypeElement type))
                continue;
            if (contracts.containsKey(type.getQualifiedName().toString()))
                continue;
            try {
                Contract contract = contract(type);
                contracts.put(type.getQualifiedName().toString(), contract);
                generateContract(contract);
            } catch (IllegalArgumentException failure) {
                error(type, failure.getMessage());
            } catch (IOException failure) {
                error(type, "插件调用代码生成失败: " + failure.getMessage());
            }
        }
        // App 基类和上下文引用已收集的契约，插件作者只使用生成的类型安全方法。
        if (!entryGenerated && !contracts.isEmpty() && option("id") != null) {
            try {
                generateEntry();
                entryGenerated = true;
            } catch (IOException failure) {
                error(null, "App 插件入口生成失败: " + failure.getMessage());
            } catch (IllegalArgumentException failure) {
                error(null, failure.getMessage());
            }
        }
        return false;
    }

    /** 取得按准确限定名标记的声明。 */
    private Set<? extends Element> annotated(RoundEnvironment round, String name) {
        TypeElement annotation = processingEnv.getElementUtils().getTypeElement(API + name);
        return annotation == null ? Set.of() : round.getElementsAnnotatedWith(annotation);
    }

    /** 构造单一契约并验证稳定操作与签名。 */
    private Contract contract(TypeElement type) {
        if (!type.getModifiers().contains(Modifier.PUBLIC) || !type.getTypeParameters().isEmpty()) {
            throw new IllegalArgumentException("契约必须是没有类型参数的 public 类型");
        }
        AnnotationMirror extension = annotation(type, "PluginExtension");
        AnnotationMirror declaration = extension == null ? annotation(type, "PluginService") : extension;
        String code = code(value(declaration, "value").toString());
        if (!codes.add(code))
            throw new IllegalArgumentException("契约编码重复: " + code);
        boolean isInterface = type.getKind() == ElementKind.INTERFACE;
        if (extension != null && !isInterface) {
            throw new IllegalArgumentException("扩展必须声明为接口");
        }
        // 接口的抽象方法必须全部声明操作；已有类只导出显式标记的方法。
        List<ExecutableElement> methods = new ArrayList<>();
        Set<String> methodCodes = new HashSet<>();
        for (Element member : processingEnv.getElementUtils().getAllMembers(type)) {
            if (member.getKind() != ElementKind.METHOD)
                continue;
            ExecutableElement method = (ExecutableElement) member;
            AnnotationMirror operation = annotation(method, "PluginOperation");
            if (operation == null) {
                if (isInterface && method.getModifiers().contains(Modifier.ABSTRACT)) {
                    throw new IllegalArgumentException("每个契约方法必须声明 PluginOperation: " + method);
                }
                continue;
            }
            if (!method.getModifiers().contains(Modifier.PUBLIC)
                    || method.getModifiers().contains(Modifier.STATIC)
                    || !method.getTypeParameters().isEmpty()
                    || method.isVarArgs()) {
                throw new IllegalArgumentException("公开操作必须是非静态、无类型参数且无可变参数的 public 方法");
            }
            if (!methodCodes.add(code(value(operation, "value").toString()))) {
                throw new IllegalArgumentException("操作编码重复: " + method);
            }
            validateType(method.getReturnType());
            method.getParameters().forEach(parameter -> validateType(parameter.asType()));
            method.getThrownTypes().forEach(this::validateType);
            methods.add(method);
        }
        if (methods.isEmpty())
            throw new IllegalArgumentException("公开契约没有操作");
        String packageName = processingEnv.getElementUtils().getPackageOf(type).getQualifiedName().toString();
        String simple = type.getSimpleName() + (isInterface ? "" : "Api");
        String selection = extension == null ? "SINGLE" : value(extension, "selection").toString();
        boolean required = extension != null && Boolean.parseBoolean(value(extension, "required").toString());
        if (isInterface)
            exported.add(type.getQualifiedName().toString());
        return new Contract(type, packageName, simple, code, extension != null, selection, required, methods);
    }

    /** 排除没有具体类型信息和异步资源语义的签名；递归收集 App 的显式数据类型。 */
    private void validateType(TypeMirror type) {
        if (type.getKind().isPrimitive() || type.getKind() == TypeKind.VOID)
            return;
        if (type instanceof ArrayType array) {
            validateType(array.getComponentType());
            return;
        }
        if (!(type instanceof DeclaredType declared)) {
            throw new IllegalArgumentException("公开签名需要具体类型: " + type);
        }
        String name = ((TypeElement) declared.asElement()).getQualifiedName().toString();
        if (name.equals("java.lang.Object")
                || name.startsWith("java.util.concurrent.")
                || name.startsWith("java.util.stream.")
                || name.startsWith("java.util.function.")) {
            throw new IllegalArgumentException("公开签名不接受任意对象或异步调用资源: " + type);
        }
        if (!declared.getTypeArguments().isEmpty()) {
            declared.getTypeArguments().forEach(this::validateType);
        }
        TypeElement element = (TypeElement) declared.asElement();
        if (annotation(element, "PluginApiType") != null) {
            exported.add(name);
            // 类型之间可以互相引用，每个类型只检查一次，避免递归数据结构无限展开。
            if (validatedTypes.add(name)) {
                for (Element member : element.getEnclosedElements()) {
                    if (member.getKind() == ElementKind.FIELD)
                        validateType(member.asType());
                    if (member.getKind() == ElementKind.METHOD && member.getModifiers().contains(Modifier.PUBLIC)) {
                        ExecutableElement method = (ExecutableElement) member;
                        if (method.getSimpleName().contentEquals("equals")
                                && method.getParameters().size() == 1
                                && method.getParameters().getFirst().asType().toString().equals("java.lang.Object"))
                            continue;
                        validateType(method.getReturnType());
                        method.getParameters().forEach(parameter -> validateType(parameter.asType()));
                    }
                }
            }
        } else if (!name.startsWith("java.")
                && !name.startsWith("javax.")
                && !sharedClass(element)) {
            throw new IllegalArgumentException("契约类型必须声明 PluginApiType 或显式共享构件: " + name);
        }
    }

    /** 从明确共享的构件读取类清单，不把全部编译依赖当作公开 API。 */
    private boolean sharedClass(TypeElement element) {
        if (!sharedLoaded) {
            sharedLoaded = true;
            String paths = option("shared");
            if (paths != null && !paths.isEmpty()) {
                try {
                    for (String file : paths.split(java.util.regex.Pattern.quote(File.pathSeparator))) {
                        Path path = Path.of(file);
                        if (Files.isDirectory(path)) {
                            try (var entries = Files.walk(path)) {
                                entries.filter(entry -> entry.toString().endsWith(".class"))
                                        .map(
                                                entry -> path.relativize(entry)
                                                        .toString()
                                                        .replace(File.separatorChar, '.'))
                                        .map(name -> name.substring(0, name.length() - 6))
                                        .forEach(sharedClasses::add);
                            }
                        } else {
                            try (JarFile jar = new JarFile(file)) {
                                jar.stream()
                                        .map(entry -> entry.getName())
                                        .filter(name -> name.endsWith(".class"))
                                        .map(name -> name.substring(0, name.length() - 6).replace('/', '.'))
                                        .forEach(sharedClasses::add);
                            }
                        }
                    }
                } catch (IOException failure) {
                    throw new IllegalArgumentException("共享构件读取失败", failure);
                }
            }
        }
        return sharedClasses.contains(processingEnv.getElementUtils().getBinaryName(element).toString());
    }

    /** 生成可阅读的客户端和分发器；运行时不通过反射执行方法。 */
    private void generateContract(Contract contract) throws IOException {
        String type = contract.packageName + "." + contract.simple;
        // 类声明先生成独立接口，插件 API 构件不包含原有宿主实现类。
        if (contract.type.getKind() != ElementKind.INTERFACE) {
            StringBuilder declaration = new StringBuilder("package " + contract.packageName + ";\n");
            declaration.append("/** 由宿主公开方法生成的插件服务接口。 */\npublic interface ")
                    .append(contract.simple)
                    .append(" {\n");
            for (ExecutableElement method : contract.methods) {
                declaration.append("/** 调用宿主公开操作。 */\n").append(signature(method)).append(";\n");
            }
            declaration.append("}\n");
            source(type, declaration.toString(), contract.type);
            exported.add(type);
        }
        // 生成操作签名和共享契约，运行时据此检查动态调用的参数与结果。
        String name = type + "Contract";
        StringBuilder body = new StringBuilder("package " + contract.packageName + ";\n");
        body.append("import com.sca4d.core.api.plugin.*;\n")
                .append("import com.sca4d.core.api.name.code.TreeCode;\n")
                .append("import java.util.*;\n")
                .append("/** 生成契约、类型安全客户端和直接调用分发器。 */\n")
                .append("@SuppressWarnings({\"unchecked\", \"rawtypes\"})\npublic final class ")
                .append(contract.simple)
                .append("Contract implements ApiContract<")
                .append(type)
                .append("> {\n")
                .append("/** 共享的契约构造器。 */\npublic static final ")
                .append(contract.simple)
                .append("Contract INSTANCE = new ")
                .append(contract.simple)
                .append("Contract();\n")
                .append("private static final ApiDefinition DEFINITION = new ApiDefinition(TreeCode.of(\"")
                .append(contract.code)
                .append("\"), ApiKind.")
                .append(contract.extension ? "EXTENSION" : "SERVICE")
                .append(", ExtensionSelection.")
                .append(contract.selection)
                .append(", ")
                .append(contract.required)
                .append(", List.of(\n");
        List<String> operations = new ArrayList<>();
        for (ExecutableElement method : contract.methods) {
            String parameters = method.getParameters()
                    .stream()
                    .map(p -> classLiteral(p.asType()))
                    .collect(Collectors.joining(","));
            String nullable = method.getParameters()
                    .stream()
                    .map(p -> Boolean.toString(nullable(p)))
                    .collect(Collectors.joining(","));
            operations.add(
                    "new ApiOperation(TreeCode.of(\"" + operationCode(method) + "\"), List.of("
                            + parameters + "), " + classLiteral(method.getReturnType()) + ", List.of(" + nullable
                            + "), " + nullable(method) + ")");
        }
        body.append(String.join(",\n", operations))
                .append("));\n")
                .append("private ")
                .append(contract.simple)
                .append("Contract() {}\n")
                .append("@Override public ApiDefinition definition() { return DEFINITION; }\n")
                .append("@Override public Class<")
                .append(type)
                .append("> type() { return ")
                .append(type)
                .append(".class; }\n")
                .append("@Override public ")
                .append(type)
                .append(" client(ApiInvoker invoker, TreeCode key) {\n")
                .append("ApiTarget target = new ApiTarget(DEFINITION.code(), key);\nreturn new ")
                .append(type)
                .append("() {\n");
        // 客户端把 Java 方法转换为稳定编码调用，并保留声明过的异常。
        for (ExecutableElement method : contract.methods) {
            body.append("@Override public ").append(signature(method)).append(" {\ntry {\n");
            String args = method.getParameters()
                    .stream()
                    .map(p -> p.getSimpleName().toString())
                    .collect(Collectors.joining(","));
            String invoke = "invoker.invoke(target, TreeCode.of(\"" + operationCode(method) + "\"), new Object[]{"
                    + args + "})";
            if (method.getReturnType().getKind() == TypeKind.VOID) {
                body.append(invoke).append(";\nreturn;\n");
            } else {
                body.append("return (").append(method.getReturnType()).append(") ").append(invoke).append(";\n");
            }
            body.append("} catch (Throwable failure) {\n");
            for (TypeMirror exception : method.getThrownTypes()) {
                body.append("if (failure instanceof ").append(exception).append(" declared) throw declared;\n");
            }
            body.append("if (failure instanceof RuntimeException runtime) throw runtime;\n")
                    .append("if (failure instanceof Error error) throw error;\n")
                    .append("throw new IllegalStateException(\"公开操作抛出未声明异常\", failure);\n}\n}\n");
        }
        // 分发器直接调用实现方法，业务执行期间不使用方法反射或网络传输。
        body.append("};\n}\n@Override public ApiBinding bind(")
                .append(type)
                .append(" implementation, TreeCode key) {\n")
                .append("Map<TreeCode, OperationInvoker> operations = new LinkedHashMap<>();\n");
        for (ExecutableElement method : contract.methods) {
            List<String> args = new ArrayList<>();
            for (int index = 0; index < method.getParameters().size(); index++) {
                args.add("(" + method.getParameters().get(index).asType() + ") arguments[" + index + "]");
            }
            body.append("operations.put(TreeCode.of(\"").append(operationCode(method)).append("\"), arguments -> ");
            String call = "implementation." + method.getSimpleName() + "(" + String.join(",", args) + ")";
            if (method.getReturnType().getKind() == TypeKind.VOID) {
                body.append("{ ").append(call).append("; return null; }");
            } else {
                body.append(call);
            }
            body.append(");\n");
        }
        body.append("return new ApiBinding(DEFINITION, new ApiTarget(DEFINITION.code(), key), operations);\n}\n}\n");
        source(name, body.toString(), contract.type);
        exported.add(name);
        // 类声明额外生成适配对象，调用目标仍是 Spring 完成代理后的宿主 Bean。
        if (contract.type.getKind() != ElementKind.INTERFACE) {
            String bindingName = type + "Binding";
            StringBuilder binding = new StringBuilder("package " + contract.packageName + ";\n")
                    .append("/** 将已有宿主类桥接到生成服务接口。 */\npublic final class ")
                    .append(contract.simple)
                    .append("Binding {\nprivate ")
                    .append(contract.simple)
                    .append("Binding() {}\n")
                    .append("/** 构造保留实际宿主代理对象的分发器。 */\npublic static com.sca4d.core.api.plugin.ApiBinding bind(")
                    .append(contract.type.getQualifiedName())
                    .append(" target) {\nreturn ")
                    .append(contract.simple)
                    .append("Contract.INSTANCE.bind(new ")
                    .append(type)
                    .append("() {\n");
            for (ExecutableElement method : contract.methods) {
                binding.append("@Override public ")
                        .append(signature(method))
                        .append(" { ")
                        .append(method.getReturnType().getKind() == TypeKind.VOID ? "" : "return ")
                        .append("target.")
                        .append(method.getSimpleName())
                        .append("(")
                        .append(
                                method.getParameters()
                                        .stream()
                                        .map(p -> p.getSimpleName().toString())
                                        .collect(Collectors.joining(",")))
                        .append("); }\n");
            }
            binding.append("}, null);\n}\n}\n");
            source(bindingName, binding.toString(), contract.type);
        }
    }

    /** 生成这个 App 自己的插件入口和类型安全上下文。 */
    private void generateEntry() throws IOException {
        String pkg = Objects.requireNonNull(option("package"));
        String entry = Objects.requireNonNull(option("entry"));
        String context = entry + "Context";
        String configuration = option("configuration");
        TypeElement configurationType = configuration == null
                ? null
                : processingEnv.getElementUtils().getTypeElement(configuration);
        if (configurationType == null
                || !configurationType.getModifiers().contains(Modifier.PUBLIC)
                || !configurationType.getTypeParameters().isEmpty()
                || annotation(configurationType, "PluginApiType") == null) {
            throw new IllegalArgumentException("App 插件配置必须声明无类型参数的 public PluginApiType");
        }
        validateType(configurationType.asType());
        StringBuilder body = new StringBuilder("package " + pkg + ";\n")
                .append("import com.sca4d.core.api.plugin.*;\n")
                .append("import com.sca4d.core.api.name.code.TreeCode;\n")
                .append("/** 为当前 App 插件提供生成的服务和扩展操作。 */\n")
                .append("public final class ")
                .append(context)
                .append(" {\nprivate final PluginContext delegate;\n")
                .append("/** 保存当前插件候选上下文。 */\npublic ")
                .append(context)
                .append("(PluginContext delegate) { this.delegate = delegate; }\n")
                .append("/** 返回当前代次的 App 公开配置。 */\npublic ")
                .append(configuration)
                .append(" configuration() { return delegate.configuration(")
                .append(configuration)
                .append(".class); }\n");
        for (Contract contract : contracts.values()) {
            String type = contract.packageName + "." + contract.simple;
            String contractName = type + "Contract.INSTANCE";
            if (contract.extension) {
                body.append("/** 登记当前插件的 ")
                        .append(contract.simple)
                        .append(" 实现。 */\npublic void register")
                        .append(contract.simple)
                        .append("(")
                        .append(type)
                        .append(" implementation")
                        .append(contract.selection.equals("KEYED") ? ", TreeCode key" : "")
                        .append(") { delegate.register(")
                        .append(contractName)
                        .append(", implementation, ")
                        .append(contract.selection.equals("KEYED") ? "key" : "null")
                        .append("); }\n");
            } else {
                String method = Character.toLowerCase(contract.simple.charAt(0)) + contract.simple.substring(1);
                body.append("/** 返回 App 公开的 ")
                        .append(contract.simple)
                        .append(" 客户端。 */\npublic ")
                        .append(type)
                        .append(" ")
                        .append(method)
                        .append("() { return delegate.service(")
                        .append(contractName)
                        .append("); }\n");
            }
        }
        body.append("}\n");
        source(pkg + "." + context, body.toString());
        source(
                pkg + "." + entry,
                "package " + pkg + ";\n"
                        + "/** 定义当前 App 的插件入口；具体实现由独立加载器加载。 */\n"
                        + "public abstract class " + entry
                        + " implements com.sca4d.core.api.plugin.PluginEntrypoint {\n"
                        + "@Override public final void onLoad(com.sca4d.core.api.plugin.PluginContext context) throws Exception { onLoad(new "
                        + context + "(context)); }\n"
                        + "/** 初始化并声明本插件的扩展。 */\npublic abstract void onLoad(" + context
                        + " context) throws Exception;\n"
                        + "@Override public void onUnload() throws Exception {}\n}\n");
        exported.add(pkg + "." + entry);
        exported.add(pkg + "." + context);
    }

    /** 输出 API 导出描述；Gradle 只打包清单中的类及其嵌套类。 */
    private void writeMetadata() throws IOException {
        Properties properties = new Properties();
        if (option("id") != null) {
            properties.setProperty("api.id", code(option("id")));
            properties.setProperty("api.version", Objects.requireNonNull(option("version")));
            properties.setProperty("api.entry", option("package") + "." + option("entry"));
            properties.setProperty("api.configuration-type", Objects.requireNonNull(option("configuration")));
            properties.setProperty(
                    "api.shared-index",
                    "META-INF/sca4dj/api-shared-"
                            + code(option("id")).replace(':', '_') + ".list");
        }
        properties.setProperty(
                "api.contracts",
                contracts.values()
                        .stream()
                        .map(c -> c.packageName + "." + c.simple + "Contract")
                        .collect(Collectors.joining(",")));
        try (Writer writer = processingEnv.getFiler()
                .createResource(
                        StandardLocation.CLASS_OUTPUT,
                        "",
                        "META-INF/sca4dj/api.properties")
                .openWriter()) {
            properties.store(writer, "Generated App plugin API");
        }
        try (Writer writer = processingEnv.getFiler()
                .createResource(
                        StandardLocation.CLASS_OUTPUT,
                        "",
                        "META-INF/sca4dj/api-classes.list")
                .openWriter()) {
            for (String name : exported)
                writer.write(name + "\n");
        }
    }

    /** 按 Java 编译类型生成方法签名，保留受检异常。 */
    private String signature(ExecutableElement method) {
        String parameters = method.getParameters()
                .stream()
                .map(p -> p.asType() + " " + p.getSimpleName())
                .collect(Collectors.joining(","));
        return method.getReturnType() + " " + method.getSimpleName() + "(" + parameters + ")"
                + (method.getThrownTypes().isEmpty()
                        ? ""
                        : " throws " + method.getThrownTypes()
                                .stream()
                                .map(Object::toString)
                                .collect(Collectors.joining(",")));
    }

    /** 返回运行时参数校验所需的擦除类型。 */
    private String classLiteral(TypeMirror type) {
        return processingEnv.getTypeUtils().erasure(type) + ".class";
    }

    /** 读取显式可空声明；未声明的引用默认不允许为空。 */
    private boolean nullable(Element element) {
        return element.getAnnotationMirrors()
                .stream()
                .anyMatch(
                        a -> a.getAnnotationType()
                                .toString()
                                .equals("org.jetbrains.annotations.Nullable"));
    }

    /** 读取方法的稳定操作编码。 */
    private String operationCode(ExecutableElement method) {
        return code(value(annotation(method, "PluginOperation"), "value").toString());
    }

    /** 使用结构化注解镜像读取属性，不解析源码文本。 */
    private AnnotationMirror annotation(Element element, String name) {
        return element.getAnnotationMirrors()
                .stream()
                .filter(a -> a.getAnnotationType().toString().equals(API + name))
                .findFirst()
                .orElse(null);
    }

    /** 读取注解属性及其默认值。 */
    private Object value(AnnotationMirror annotation, String name) {
        return processingEnv.getElementUtils()
                .getElementValuesWithDefaults(annotation)
                .entrySet()
                .stream()
                .filter(e -> e.getKey().getSimpleName().contentEquals(name))
                .findFirst()
                .orElseThrow()
                .getValue()
                .getValue();
    }

    /** 校验稳定编码并归一为 TreeCode 的冒号输出。 */
    private String code(String value) {
        long separatorKinds = "/.:".chars().filter(separator -> value.indexOf(separator) >= 0).count();
        if (separatorKinds > 1)
            throw new IllegalArgumentException("公开编码不能混用分隔符: " + value);
        String normalized = value.replace('/', ':').replace('.', ':').toUpperCase(Locale.ROOT);
        if (!normalized.matches("[A-Z][A-Z0-9_]*(?::[A-Z][A-Z0-9_]*)*")) {
            throw new IllegalArgumentException("公开编码不符合 TreeCode 规范: " + value);
        }
        return normalized;
    }

    /** 返回 Gradle 传入的 App 描述属性。 */
    private String option(String name) {
        return processingEnv.getOptions().get("sca4dj.plugin.api." + name);
    }

    /** 通过 Filer 写入生成源码，保留来源元素用于增量编译。 */
    private void source(String name, String body, Element... origins) throws IOException {
        try (Writer writer = processingEnv.getFiler().createSourceFile(name, origins).openWriter()) {
            writer.write(body);
        }
    }

    /** 将声明错误定位到实际源码。 */
    private void error(Element element, String message) {
        processingEnv.getMessager().printMessage(Diagnostic.Kind.ERROR, message, element);
    }

    /** 保存生成一个契约所需的编译期类型和方法。 */
    private static final class Contract {
        private final TypeElement type;

        private final String packageName;

        private final String simple;

        private final String code;

        private final boolean extension;

        private final String selection;

        private final boolean required;

        private final List<ExecutableElement> methods;

        private Contract(
                TypeElement type,
                String packageName,
                String simple,
                String code,
                boolean extension,
                String selection,
                boolean required,
                List<ExecutableElement> methods) {
            this.type = type;
            this.packageName = packageName;
            this.simple = simple;
            this.code = code;
            this.extension = extension;
            this.selection = selection;
            this.required = required;
            this.methods = methods;
        }
    }
}
