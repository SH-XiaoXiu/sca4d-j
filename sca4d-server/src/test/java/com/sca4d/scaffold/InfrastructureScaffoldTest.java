package com.sca4d.scaffold;

import static org.assertj.core.api.Assertions.assertThat;

import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

/** 验证公共组件包依赖、Spring 装配和禁止调用规则。 */
class InfrastructureScaffoldTest {

    private static final Path MAIN = Path.of("src/main/java");

    @Test
    void productionAndTestCodeShouldNotDeclareRecords() throws Exception {
        Pattern declaration = Pattern.compile(
                "(?m)^\\s*(?:(?:public|protected|private|static|final|sealed|non-sealed)\\s+)*"
                        + "record\\s+[A-Za-z_$][A-Za-z0-9_$]*\\s*\\(");

        assertThat(declaration.matcher(readTree(MAIN)).find()).isFalse();
        assertThat(declaration.matcher(readTree(Path.of("src/test/java"))).find()).isFalse();
    }

    @Test
    void businessModulesShouldNotUseRedisTemplateDirectly() throws Exception {
        for (String packageName : List.of("system", "authorization", "notification", "im", "controllers")) {
            Path packagePath = MAIN.resolve("com/sca4d/" + packageName);
            if (!Files.exists(packagePath)) {
                continue;
            }
            String source = readTree(packagePath);

            assertThat(source)
                    .doesNotContain("org.springframework.data.redis.core.RedisTemplate")
                    .doesNotContain("org.springframework.data.redis.core.StringRedisTemplate")
                    .doesNotContain("org.springframework.data.redis.core.ReactiveRedisTemplate");
        }
    }

    @Test
    void mainCodeShouldUseRequiredForRuntimeNonNullValidation() throws Exception {
        String source = readTree(MAIN)
                .replace(read(MAIN.resolve("com/sca4d/common/validation/Required.java")), "");

        assertThat(source)
                .doesNotContain("import jakarta.validation.constraints.NotNull")
                .doesNotContain("@jakarta.validation.constraints.NotNull");
    }

    @Test
    void securityRuntimeShouldNotKeepAuthorizationPackageOrHiddenCacheDependencies()
            throws Exception {
        assertThat(MAIN.resolve("com/sca4d/authorization")).doesNotExist();
        Path authentication = MAIN.resolve("com/sca4d/authentication");
        String source = readTree(authentication)
                + readTree(MAIN.resolve("com/sca4d/permission"));

        assertThat(source)
                .doesNotContain("com.sca4d.old")
                .doesNotContain("@Cacheable")
                .doesNotContain("org.springframework.data.redis.core.RedisTemplate");
        List<Path> redisConsumers = new ArrayList<>();
        try (var files = Files.walk(authentication)) {
            files.filter(Files::isRegularFile)
                    .filter(path -> path.toString().endsWith(".java"))
                    .filter(path -> read(path).contains(
                            "org.springframework.data.redis.core.StringRedisTemplate"))
                    .forEach(redisConsumers::add);
        }
        assertThat(redisConsumers).isEmpty();
    }

    @Test
    void cedarDslShouldLoadMainSchemaInsteadOfGeneratingAuthorizationModel() throws Exception {
        String rules = read(MAIN.resolve("com/sca4d/common/access/Rules.java"));
        String tenantPolicy = read(MAIN.resolve("com/sca4d/tenant/policy/TenantAccessPolicy.java"));
        Path tenantSchema = Path.of("src/main/resources/cedar/tenant-access/schema.cedar");

        assertThat(rules)
                .contains("schemaResource", "Policy.generated(")
                .doesNotContain(
                        "entity Principal =",
                        ".append(\"entity ",
                        ".append(\"action ",
                        "schema.append(");
        assertThat(tenantPolicy)
                .contains("private static final Policy<TenantResource> POLICY")
                .contains("/cedar/tenant-access/schema.cedar");
        assertThat(tenantSchema).isRegularFile();
        assertThat(read(tenantSchema))
                .contains("entity Principal", "entity TenantResource", "action \"READ\"");
    }

    @Test
    void credentialSchemesShouldOnlyUseProtocolRepositoriesForPersistence() throws Exception {
        List<Path> schemes = new ArrayList<>();
        try (var files = Files.walk(MAIN.resolve("com/sca4d/iam"))) {
            files.filter(Files::isRegularFile)
                    .filter(path -> path.toString().endsWith(".java"))
                    .filter(path -> read(path).contains("implements CredentialScheme<"))
                    .forEach(schemes::add);
        }

        assertThat(schemes).isNotEmpty();
        for (Path scheme : schemes) {
            assertThat(read(scheme))
                    .containsPattern("import com\\.sca4d\\.iam(?:\\.[a-z]+)*\\.repository\\.")
                    .doesNotContain("import com.sca4d.mapper.");
        }
    }

    @Test
    void iamRelationsShouldOnlyBeMaintainedThroughIamRepositoryAndIamMapper() throws Exception {
        List<Path> directIamMapperConsumers = new ArrayList<>();
        List<Path> relationEntityConsumers = new ArrayList<>();
        try (var files = Files.walk(MAIN)) {
            files.filter(Files::isRegularFile)
                    .filter(path -> path.toString().endsWith(".java"))
                    .forEach(path -> {
                        String source = read(path);
                        if (source.contains("import com.sca4d.mapper.IamMapper")) {
                            directIamMapperConsumers.add(path);
                        }
                        if ((source.contains("import com.sca4d.entity.IamPrincipalRole")
                                || source.contains("import com.sca4d.entity.IamRolePermission"))
                                && !path.endsWith("com/sca4d/mapper/IamMapper.java")) {
                            relationEntityConsumers.add(path);
                        }
                    });
        }

        Path iamRepository = MAIN.resolve("com/sca4d/iam/repository/IamRepository.java");
        assertThat(directIamMapperConsumers).containsExactly(iamRepository);
        assertThat(relationEntityConsumers).containsExactly(iamRepository);

    }

    @Test
    void logbackShouldUseProfileSpecificConsoleAndFileLevels() throws Exception {
        String logback = Files.readString(Path.of("src/main/resources/logback-spring.xml"));
        String application = Files.readString(Path.of("src/main/resources/application.yml"));

        assertThat(logback).contains("springProfile name=\"prod\"");
        assertThat(logback).contains("DEBUG_FILE", "ERROR_FILE", "ACCESS_FILE");
        assertThat(logback).contains("debug.%d{yyyy-MM-dd}.%i.log.gz");
        assertThat(logback).contains("error.%d{yyyy-MM-dd}.%i.log.gz");
        assertThat(logback).contains("access.%d{yyyy-MM-dd}.%i.log.gz");
        assertThat(logback).contains("springProfile name=\"dev | local\"");
        assertThat(logback).contains("TRACE_FILE", "trace.%d{yyyy-MM-dd}.%i.log.gz");
        assertThat(logback).contains("springProfile name=\"!prod &amp; !dev &amp; !local\"");
        assertThat(logback)
                .contains("<level>DEBUG</level>")
                .contains("<level>TRACE</level>")
                .contains("<root level=\"DEBUG\">")
                .contains("<root level=\"INFO\">")
                .contains("<logger name=\"com.sca4d\" level=\"TRACE\" additivity=\"false\">")
                .contains("conversionWord=\"source\"")
                .contains("com.sca4d.common.logging.LogSourceConverter")
                .contains("%-5level %source")
                .contains("<logger name=\"com.sca4d.mapper\" level=\"INFO\"/>")
                .contains("<logger name=\"org.apache.coyote\" level=\"INFO\"/>")
                .contains("<logger name=\"org.apache.tomcat.util.http\" level=\"INFO\"/>")
                .contains("<logger name=\"org.springframework.web\" level=\"DEBUG\"/>")
                .contains("<logger name=\"org.springframework.web.servlet.DispatcherServlet\" level=\"INFO\"/>")
                .contains("<logger name=\"org.redisson.client.handler\" level=\"DEBUG\"/>")
                .contains("<logger name=\"org.postgresql\" level=\"INFO\"/>")
                .doesNotContain("<root level=\"TRACE\">", "%class", "%caller");
        assertThat(application)
                .contains("on-profile: \"local | dev\"")
                .contains("log-impl: org.apache.ibatis.logging.stdout.StdOutImpl");
    }

    /**
     * 拼接指定源码目录下全部普通文件内容，供包级依赖规则断言使用。
     *
     * @param path 待扫描源码目录
     * @return 目录树全部普通文件的文本
     * @throws Exception 目录遍历或文件读取失败时抛出
     */
    private @NotNull String readTree(@NotNull Path path) throws Exception {
        try (var files = Files.walk(path)) {
            return files.filter(Files::isRegularFile)
                    .map(this::read)
                    .reduce("", String::concat);
        }
    }

    /**
     * 读取单个架构断言文件，并保留失败文件路径。
     *
     * @param path 待读取文件
     * @return 文件完整文本
     */
    private @NotNull String read(@NotNull Path path) {
        try {
            return Files.readString(path);
        } catch (Exception ex) {
            throw new IllegalStateException("读取文件失败: " + path, ex);
        }
    }
}
