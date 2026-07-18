package com.sca4d.common.dictionary;

import static org.assertj.core.api.Assertions.assertThat;

import com.sca4d.common.i18n.LocalizedMessages;
import com.sca4d.common.i18n.LocalizedMessagesFixture;
import com.sca4d.common.i18n.SupportedLocale;
import org.junit.jupiter.api.Test;

import java.util.Set;

/** 验证代码字典类型、字典项和值的声明规则。 */
class CodeDictionaryContractTest {

    @Test
    void shouldPublishOnlyDictionariesConsumedByTheCurrentAdministrationApplication() {
        CodeDictionaryCatalog catalog = new CodeDictionaryCatalog(new CodeDictionaryScanner().scan(Set.of("com.sca4d")));

        assertThat(catalog.definitions())
                .extracting(CodeDictionaryDefinition::getCode)
                .containsExactly(
                        "common.access-action",
                        "iam.account-scope-type",
                        "iam.account-status",
                        "iam.credential-status",
                        "iam.credential-type",
                        "iam.membership-status",
                        "iam.principal-status",
                        "iam.role-status",
                        "iam.subject-status",
                        "iam.tenant-status",
                        "iam.tenant-type",
                        "system.audit-result",
                        "system.dictionary-kind",
                        "system.dictionary-status",
                        "system.dictionary-tone");
    }

    @Test
    void shouldProvideEveryCodeDictionaryLabelInAllSupportedLanguages() {
        CodeDictionaryCatalog catalog = new CodeDictionaryCatalog(new CodeDictionaryScanner().scan(Set.of("com.sca4d")));
        LocalizedMessages messages = LocalizedMessagesFixture.create();

        for (SupportedLocale locale : SupportedLocale.values()) {
            catalog.definitions().forEach(definition -> {
                assertThat(messages.get(locale.locale(), definition.getNameKey())).isNotBlank();
                definition.getOptions().stream()
                        .filter(CodeDictionaryOption::isPublished)
                        .forEach(option -> assertThat(messages.get(locale.locale(), option.getLabelKey())).isNotBlank());
            });
        }
    }
}
