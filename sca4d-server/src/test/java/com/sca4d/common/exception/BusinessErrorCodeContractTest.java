package com.sca4d.common.exception;

import static org.assertj.core.api.Assertions.assertThat;

import com.sca4d.common.auth.ForbiddenException;
import com.sca4d.common.auth.UnauthenticatedException;
import com.sca4d.common.concurrent.ConcurrencyConflictException;
import com.sca4d.common.concurrent.lock.LockBusyException;
import com.sca4d.common.concurrent.ratelimit.RateLimitException;
import com.sca4d.common.i18n.LocalizedMessages;
import com.sca4d.common.i18n.LocalizedMessagesFixture;
import com.sca4d.common.i18n.SupportedLocale;
import com.sca4d.iam.error.IamErrorCode;
import com.sca4d.role.RoleErrorCode;
import com.sca4d.tenant.TenantErrorCode;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/** 验证业务错误码号段、消息键和响应分类没有冲突。 */
class BusinessErrorCodeContractTest {

    @Test
    void shouldProvideUniqueTranslatedMessagesForEveryBusinessErrorCode() throws Exception {
        ErrorCodeCatalog.Builder builder = ErrorCodeCatalog.builder();
        for (Class<? extends Enum<?>> enumType : new ErrorCodeScanner().scan(Set.of("com.sca4d"))) {
            if (enumType.getEnclosingClass() == null) {
                builder.register(enumType);
            }
        }
        ErrorCodeCatalog catalog = builder.build();
        assertThat(catalog.items()).extracting(ErrorCodeItem::getMessageKey).doesNotHaveDuplicates();

        LocalizedMessages messages = LocalizedMessagesFixture.create();
        for (ErrorCodeItem item : catalog.items()) {
            assertThat(messages.get(Locale.ROOT, item.getMessageKey()))
                    .as("%s 缺少根消息", item.getMessageKey())
                    .isNotBlank();
        }
        for (SupportedLocale locale : SupportedLocale.values()) {
            for (ErrorCodeItem item : catalog.items()) {
                assertThat(messages.get(locale.locale(), item.getMessageKey()))
                        .as("%s 缺少 %s", locale.value(), item.getMessageKey())
                        .isNotBlank();
            }
        }
    }

    @Test
    void shouldNotPassMessageKeyToBizException() throws Exception {
        assertThat(List.of(
                BizException.class,
                ForbiddenException.class,
                UnauthenticatedException.class,
                LockBusyException.class,
                RateLimitException.class))
                .allSatisfy(type -> assertThat(type.getConstructors())
                        .noneSatisfy(constructor -> assertThat(constructor.getParameterTypes())
                                .as("%s 不得开放 messageKey 参数", type.getSimpleName())
                                .contains(String.class)));

        List<String> violations = new ArrayList<>();
        try (var files = Files.walk(Path.of("src/main/java"))) {
            for (Path file : files.filter(path -> path.toString().endsWith(".java")).toList()) {
                String source = Files.readString(file);
                if (source.matches(
                        "(?s).*new (?:BizException|ForbiddenException|UnauthenticatedException|LockBusyException|RateLimitException)\\([\\s\\S]{0,240}\"(?:error|validation)\\.[^\"]+\".*")) {
                    violations.add(file.toString());
                }
            }
        }

        assertThat(violations).isEmpty();
    }

    @Test
    void shouldNotPassMessageKeyToConcurrencyConflictException() throws Exception {
        assertThat(ConcurrencyConflictException.class.getConstructors())
                .filteredOn(constructor -> constructor.getParameterCount() > 1)
                .allSatisfy(constructor -> assertThat(constructor.getParameterTypes()[0])
                        .as("并发冲突异常不得重新开放 messageKey 参数")
                        .isNotEqualTo(String.class));

        List<String> violations = new ArrayList<>();
        try (var files = Files.walk(Path.of("src/main/java"))) {
            for (Path file : files.filter(path -> path.toString().endsWith(".java")).toList()) {
                String source = Files.readString(file);
                if (source.matches(
                        "(?s).*new ConcurrencyConflictException\\([\\s\\S]{0,120}\\\"(?:error|validation)\\.[^\\\"]+\\\".*")) {
                    violations.add(file.toString());
                }
            }
        }

        assertThat(violations).isEmpty();
    }

    @Test
    void shouldKeepManagementDomainErrorCodesInTheirOwningModules() {
        assertThat(IamErrorCode.values())
                .allSatisfy(errorCode -> assertThat(errorCode.getMessageKey()).startsWith("error.iam."));
        assertThat(RoleErrorCode.values())
                .allSatisfy(errorCode -> assertThat(errorCode.getMessageKey()).startsWith("error.role."));
        assertThat(TenantErrorCode.values())
                .allSatisfy(errorCode -> assertThat(errorCode.getMessageKey()).startsWith("error.tenant."));
    }
}
