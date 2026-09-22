package com.sca4dj.gradle.app;

import org.gradle.api.DefaultTask;
import org.gradle.api.GradleException;
import org.gradle.api.artifacts.Configuration;
import org.gradle.api.artifacts.ResolvedArtifact;
import org.gradle.api.artifacts.component.ComponentIdentifier;
import org.gradle.api.artifacts.result.ResolvedComponentResult;
import org.gradle.api.artifacts.result.ResolvedDependencyResult;
import org.gradle.api.file.ConfigurableFileCollection;
import org.gradle.api.file.DirectoryProperty;
import org.gradle.api.file.RegularFileProperty;
import org.gradle.api.provider.Property;
import org.gradle.api.tasks.Classpath;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.InputFile;
import org.gradle.api.tasks.Internal;
import org.gradle.api.tasks.Optional;
import org.gradle.api.tasks.PathSensitive;
import org.gradle.api.tasks.PathSensitivity;
import org.gradle.api.tasks.TaskAction;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/** 从 App 运行依赖的结构片段生成应用自己负责的 Flyway V1。 */
public abstract class InitFlywayTask extends DefaultTask {

    private static final Pattern MIGRATION_FILE = Pattern.compile("V[^/]*\\.sql");
    private static final Pattern MIGRATION_NAME = Pattern.compile("[a-z0-9]+(?:_[a-z0-9]+)*");
    private static final Pattern SCHEMA_ENTRY = Pattern.compile("^META-INF/sca4dj/schema/([^/]+)\\.sql$");

    /**
     * 返回用于定位结构片段和依赖顺序的运行配置属性。
     *
     * @return 保存 App `runtimeClasspath` 的 Gradle 属性
     */
    @Internal
    public abstract Property<Configuration> getRuntimeConfiguration();

    /**
     * 返回参与生成的运行依赖文件。
     *
     * @return Gradle 运行类路径
     */
    @Classpath
    public abstract ConfigurableFileCollection getRuntimeClasspath();

    /**
     * 返回初始迁移文件使用的稳定名称。
     *
     * @return 不包含版本和扩展名的迁移名称
     */
    @Input
    public abstract Property<String> getMigrationName();

    /**
     * 返回可选的 App 自有数据库结构片段。
     *
     * @return 依赖模块片段之后写入的 SQL 文件
     */
    @Optional
    @InputFile
    @PathSensitive(PathSensitivity.RELATIVE)
    public abstract RegularFileProperty getSchemaFragment();

    /**
     * 返回 App 自己维护的 Flyway 迁移目录。
     *
     * @return `src/main/resources/db/migration`
     */
    @Internal
    public abstract DirectoryProperty getMigrationDirectory();

    /**
     * 读取模块结构片段，按组件依赖顺序生成不可覆盖的 V1。
     *
     * @throws IOException 无法读取依赖或原子写入迁移文件时抛出
     */
    @TaskAction
    public void generateMigration() throws IOException {
        String migrationName = getMigrationName().get();
        if (!MIGRATION_NAME.matcher(migrationName).matches()) {
            throw new GradleException("sca4djApp.migrationName 只能使用小写字母、数字和单个下划线分隔："
                    + migrationName);
        }
        Path migrationDirectory = getMigrationDirectory().get().getAsFile().toPath();
        rejectExistingMigrations(migrationDirectory);

        Configuration runtimeConfiguration = getRuntimeConfiguration().get();
        Map<ComponentIdentifier, ResolvedComponentResult> components = new HashMap<>();
        runtimeConfiguration.getIncoming().getResolutionResult().getAllComponents()
                .forEach(component -> components.put(component.getId(), component));

        List<ResolvedArtifact> artifacts = new ArrayList<>(
                runtimeConfiguration.getResolvedConfiguration().getResolvedArtifacts());
        artifacts.sort(Comparator.comparing(artifact -> artifact.getId().getComponentIdentifier().getDisplayName()));

        Map<ComponentIdentifier, SchemaFragment> fragments = new LinkedHashMap<>();
        Map<String, String> fragmentOwners = new HashMap<>();
        for (ResolvedArtifact artifact : artifacts) {
            ComponentIdentifier componentId = artifact.getId().getComponentIdentifier();
            List<SchemaFragment> componentFragments = readFragments(artifact);
            if (componentFragments.size() > 1) {
                throw new GradleException("组件 " + componentId.getDisplayName() + " 提供了多个数据库结构片段："
                        + componentFragments.stream().map(fragment -> fragment.name).sorted().toList());
            }
            if (componentFragments.isEmpty()) {
                continue;
            }
            SchemaFragment fragment = componentFragments.getFirst();
            if (fragment.content.isBlank()) {
                throw new GradleException("组件 " + componentId.getDisplayName() + " 的数据库结构片段 "
                        + fragment.name + ".sql 为空");
            }
            String previousOwner = fragmentOwners.putIfAbsent(fragment.name, componentId.getDisplayName());
            if (previousOwner != null) {
                throw new GradleException("数据库结构片段名称 " + fragment.name + ".sql 重复：" + previousOwner
                        + "、" + componentId.getDisplayName());
            }
            fragments.put(componentId, fragment);
        }
        SchemaFragment appFragment = readAppFragment();
        if (fragments.isEmpty() && appFragment == null) {
            throw new GradleException("当前 App 的运行模块和 schemaFragment 都没有提供数据库结构片段");
        }

        List<ComponentIdentifier> orderedComponents = orderFragments(components, fragments.keySet());
        Path output = migrationDirectory.resolve("V1__init_" + migrationName + ".sql");
        writeMigration(output, orderedComponents, fragments, appFragment);
        getLogger().lifecycle("已生成 {}", getProject().relativePath(output.toFile()));
    }

    /** 读取并校验 App 自己维护的可选结构片段。 */
    private SchemaFragment readAppFragment() throws IOException {
        if (!getSchemaFragment().isPresent()) {
            return null;
        }
        Path path = getSchemaFragment().get().getAsFile().toPath();
        String fileName = Objects.requireNonNull(path.getFileName(), "App 数据库结构片段必须包含文件名")
                .toString();
        if (!fileName.endsWith(".sql")) {
            throw new GradleException("sca4djApp.schemaFragment 必须指向 .sql 文件：" + fileName);
        }
        String content = Files.readString(path, StandardCharsets.UTF_8);
        if (content.isBlank()) {
            throw new GradleException("sca4djApp.schemaFragment 不能为空：" + fileName);
        }
        return new SchemaFragment(fileName.substring(0, fileName.length() - 4), content);
    }

    /** 拒绝覆盖 App 已经拥有的任意 Flyway 迁移。 */
    private static void rejectExistingMigrations(Path migrationDirectory) throws IOException {
        if (!Files.exists(migrationDirectory)) {
            return;
        }
        List<String> migrations;
        try (var paths = Files.list(migrationDirectory)) {
            migrations = paths.filter(Files::isRegularFile)
                    .map(path -> Objects.requireNonNull(path.getFileName(), "Flyway 迁移必须包含文件名").toString())
                    .filter(name -> MIGRATION_FILE.matcher(name).matches())
                    .sorted()
                    .toList();
        }
        if (!migrations.isEmpty()) {
            throw new GradleException("App 已存在 Flyway 迁移，初始化任务不会覆盖：" + String.join(", ", migrations));
        }
    }

    /** 读取一个依赖组件提供的结构片段。 */
    private static List<SchemaFragment> readFragments(ResolvedArtifact artifact) throws IOException {
        List<SchemaFragment> fragments = new ArrayList<>();
        if (artifact.getFile().isDirectory()) {
            Path schemaDirectory = artifact.getFile().toPath().resolve("META-INF/sca4dj/schema");
            if (!Files.isDirectory(schemaDirectory)) {
                return fragments;
            }
            try (var paths = Files.list(schemaDirectory)) {
                for (Path path : paths.filter(Files::isRegularFile).sorted().toList()) {
                    String fileName = Objects.requireNonNull(path.getFileName(), "数据库结构片段必须包含文件名")
                            .toString();
                    if (fileName.endsWith(".sql")) {
                        fragments.add(new SchemaFragment(fileName.substring(0, fileName.length() - 4),
                                Files.readString(path, StandardCharsets.UTF_8)));
                    }
                }
            }
            return fragments;
        }

        try (ZipFile archive = new ZipFile(artifact.getFile())) {
            List<? extends ZipEntry> entries = archive.stream()
                    .filter(entry -> !entry.isDirectory() && SCHEMA_ENTRY.matcher(entry.getName()).matches())
                    .sorted(Comparator.comparing(ZipEntry::getName))
                    .toList();
            for (ZipEntry entry : entries) {
                Matcher matcher = SCHEMA_ENTRY.matcher(entry.getName());
                if (!matcher.matches()) {
                    continue;
                }
                try (InputStream input = archive.getInputStream(entry)) {
                    fragments.add(new SchemaFragment(matcher.group(1),
                            new String(input.readAllBytes(), StandardCharsets.UTF_8)));
                }
            }
        }
        return fragments;
    }

    /** 按结构片段所属组件之间的传递依赖关系排序。 */
    private static List<ComponentIdentifier> orderFragments(
            Map<ComponentIdentifier, ResolvedComponentResult> components,
            Set<ComponentIdentifier> fragmentComponents) {
        Map<ComponentIdentifier, Set<ComponentIdentifier>> pending = new LinkedHashMap<>();
        for (ComponentIdentifier componentId : fragmentComponents) {
            Set<ComponentIdentifier> dependencies = new LinkedHashSet<>();
            collectFragmentDependencies(components.get(componentId), componentId, fragmentComponents,
                    new HashSet<>(), dependencies);
            pending.put(componentId, dependencies);
        }

        List<ComponentIdentifier> ordered = new ArrayList<>();
        while (!pending.isEmpty()) {
            ComponentIdentifier next = pending.entrySet().stream()
                    .filter(entry -> entry.getValue().isEmpty())
                    .map(Map.Entry::getKey)
                    .min(Comparator.comparing(ComponentIdentifier::getDisplayName))
                    .orElseThrow(() -> new GradleException("数据库结构片段依赖存在环："
                            + pending.keySet().stream().map(ComponentIdentifier::getDisplayName).sorted().toList()));
            ordered.add(next);
            pending.remove(next);
            pending.values().forEach(dependencies -> dependencies.remove(next));
        }
        return ordered;
    }

    /** 收集一个组件传递依赖的其他结构片段组件。 */
    private static void collectFragmentDependencies(
            ResolvedComponentResult component,
            ComponentIdentifier owner,
            Set<ComponentIdentifier> fragmentComponents,
            Set<ComponentIdentifier> visited,
            Set<ComponentIdentifier> dependencies) {
        if (component == null) {
            return;
        }
        component.getDependencies().stream()
                .filter(ResolvedDependencyResult.class::isInstance)
                .map(ResolvedDependencyResult.class::cast)
                .forEach(dependency -> {
                    ComponentIdentifier dependencyId = dependency.getSelected().getId();
                    if (!visited.add(dependencyId)) {
                        return;
                    }
                    if (!dependencyId.equals(owner) && fragmentComponents.contains(dependencyId)) {
                        dependencies.add(dependencyId);
                    }
                    collectFragmentDependencies(dependency.getSelected(), owner, fragmentComponents, visited, dependencies);
                });
    }

    /** 使用临时文件原子写入完整迁移，避免失败后留下部分 SQL。 */
    private static void writeMigration(
            Path output,
            List<ComponentIdentifier> orderedComponents,
            Map<ComponentIdentifier, SchemaFragment> fragments,
            SchemaFragment appFragment) throws IOException {
        StringBuilder migration = new StringBuilder();
        migration.append("-- 此文件由 initFlyway 根据 App 运行模块生成。\n");
        migration.append("-- 生成后由 App 维护，后续数据库变化请新增 V2、V3 等迁移。\n\n");
        for (int index = 0; index < orderedComponents.size(); index++) {
            ComponentIdentifier componentId = orderedComponents.get(index);
            SchemaFragment fragment = Objects.requireNonNull(
                    fragments.get(componentId), "有序组件必须对应数据库结构片段: " + componentId.getDisplayName());
            String content = fragment.content.replace("\r\n", "\n").replace('\r', '\n').stripTrailing();
            migration.append("-- 组件：").append(componentId.getDisplayName()).append('\n');
            migration.append("-- 结构：").append(fragment.name).append(".sql\n");
            migration.append(content).append('\n');
            if (index + 1 < orderedComponents.size() || appFragment != null) {
                migration.append('\n');
            }
        }
        if (appFragment != null) {
            String content = appFragment.content.replace("\r\n", "\n").replace('\r', '\n').stripTrailing();
            migration.append("-- App：自有结构\n");
            migration.append("-- 结构：").append(appFragment.name).append(".sql\n");
            migration.append(content).append('\n');
        }

        Path outputDirectory = Objects.requireNonNull(output.getParent(), "Flyway 迁移输出必须位于目录内");
        Files.createDirectories(outputDirectory);
        Path temporaryFile = Files.createTempFile(outputDirectory, ".V1__init_sca4dj-", ".sql.tmp");
        try {
            Files.writeString(temporaryFile, migration, StandardCharsets.UTF_8);
            try {
                Files.move(temporaryFile, output, StandardCopyOption.ATOMIC_MOVE);
            } catch (AtomicMoveNotSupportedException exception) {
                throw new GradleException("当前文件系统不支持原子生成 Flyway 初始迁移", exception);
            }
        } finally {
            Files.deleteIfExists(temporaryFile);
        }
    }

    /** 保存一个组件中唯一结构片段的名称和 SQL。 */
    @SuppressWarnings("ClassCanBeRecord") // 项目统一使用普通 class，不使用 Java record。
    private static final class SchemaFragment {

        private final String name;
        private final String content;

        private SchemaFragment(String name, String content) {
            this.name = name;
            this.content = content;
        }

    }
}
