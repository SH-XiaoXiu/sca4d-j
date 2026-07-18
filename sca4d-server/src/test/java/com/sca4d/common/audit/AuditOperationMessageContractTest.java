package com.sca4d.common.audit;

import static org.assertj.core.api.Assertions.assertThat;

import com.sca4d.authentication.audit.AuthenticationAuditOperations;
import com.sca4d.common.i18n.LocalizedMessages;
import com.sca4d.common.i18n.LocalizedMessagesFixture;
import com.sca4d.common.i18n.SupportedLocale;
import com.sca4d.iam.profile.audit.DefaultProfileAuditOperations;
import com.sca4d.platform.audit.PlatformAuditOperations;
import com.sca4d.system.audit.SystemAuditOperations;
import com.sca4d.tenant.audit.TenantAuditOperations;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.ClassPathScanningCandidateComponentProvider;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.core.type.filter.TypeFilter;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/** 验证所有审计动作码都有中文和英文消息，并且参数数量一致。 */
class AuditOperationMessageContractTest {

    @Test
    void shouldRegisterEveryControllerAuditDeclaration() throws Exception {
        AuditOperationCatalog catalog = new AuditOperationCatalog(List.of(
                new AuthenticationAuditOperations(),
                new DefaultProfileAuditOperations(),
                new PlatformAuditOperations(),
                new TenantAuditOperations(),
                new SystemAuditOperations()));
        ClassPathScanningCandidateComponentProvider scanner = new ClassPathScanningCandidateComponentProvider(false);
        scanner.addIncludeFilter((TypeFilter) (metadataReader, metadataReaderFactory) -> true);
        List<String> declarations = new ArrayList<>();
        for (var candidate : scanner.findCandidateComponents("com.sca4d.controllers")) {
            Class<?> type = Class.forName(candidate.getBeanClassName());
            Audit typeAudit = AnnotatedElementUtils.findMergedAnnotation(type, Audit.class);
            if (typeAudit != null) {
                catalog.require(typeAudit.module(), typeAudit.action());
                declarations.add(typeAudit.module() + ":" + typeAudit.action());
            }
            for (var method : type.getDeclaredMethods()) {
                Audit audit = AnnotatedElementUtils.findMergedAnnotation(method, Audit.class);
                if (audit == null) {
                    continue;
                }
                catalog.require(audit.module(), audit.action());
                declarations.add(audit.module() + ":" + audit.action());
            }
        }

        assertThat(declarations).isNotEmpty();
    }

    @Test
    void shouldNotDeclareAuditModuleOrActionAsStringLiteral() throws Exception {
        List<String> violations = new ArrayList<>();
        try (var files = Files.walk(Path.of("src/main/java"))) {
            for (Path file : files.filter(path -> path.toString().endsWith(".java")).toList()) {
                String source = Files.readString(file);
                if (source.contains("@Audit")
                        && (source.matches("(?s).*@Audit\\([^)]*module\\s*=\\s*\".*")
                                || source.matches("(?s).*@Audit\\([^)]*action\\s*=\\s*\".*"))) {
                    violations.add(file.toString());
                }
            }
        }

        assertThat(violations).isEmpty();
    }

    @Test
    void shouldResolveEveryAuditCatalogMessageInAllSupportedLanguages() {
        AuditOperationCatalog catalog = new AuditOperationCatalog(List.of(
                new AuthenticationAuditOperations(),
                new DefaultProfileAuditOperations(),
                new PlatformAuditOperations(),
                new TenantAuditOperations(),
                new SystemAuditOperations()));
        LocalizedMessages messages = LocalizedMessagesFixture.create();

        for (SupportedLocale locale : SupportedLocale.values()) {
            for (AuditOperationDefinition operation : catalog.operations()) {
                assertThat(messages.get(locale.locale(), operation.getModuleNameKey())).isNotBlank();
                assertThat(messages.get(locale.locale(), operation.getActionNameKey())).isNotBlank();
                assertThat(messages.get(locale.locale(), operation.getSummaryKey())).isNotBlank();
            }
        }
    }
}
