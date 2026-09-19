package com.sca4d.app.server;

import static org.assertj.core.api.Assertions.assertThat;

import com.sca4d.app.server.audit.SystemOperations;
import com.sca4d.app.server.iot.IotOperations;
import com.sca4d.auth.email.EmailCodeErrorCode;
import com.sca4d.auth.passkey.PasskeyErrorCode;
import com.sca4d.auth.password.PasswordErrorCode;
import com.sca4d.auth.totp.TotpErrorCode;
import com.sca4d.core.api.authentication.AuthErrorCode;
import com.sca4d.core.api.error.CoreErrorCode;
import com.sca4d.core.api.error.ErrorCode;
import com.sca4d.core.api.i18n.LocaleCatalog;
import com.sca4d.core.api.i18n.MessageCatalog;
import com.sca4d.core.api.permission.OperationCatalog;
import com.sca4d.core.api.permission.OperationNode;
import com.sca4d.core.api.storage.FileErrorCode;
import com.sca4d.core.spring.i18n.ClasspathMessageCatalog;
import com.sca4d.core.spring.web.HttpErrorCode;
import com.sca4d.iam.base.rbac.PlatformOperations;
import com.sca4d.iam.identity.IamErrorCode;
import com.sca4d.iam.rbac.RbacErrorCode;
import com.sca4d.iot.IotErrorCode;
import com.sca4d.iot.api.message.IotFailureCode;
import com.sca4d.notification.email.EmailNotificationErrorCode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.stream.Stream;

/** 校验默认应用安装模块的错误码和 Operation 消息键均可按支持语言解析。 */
class AppMessageCatalogContractTest {

    /** 默认应用公开支持的 Locale。 */
    private static final List<Locale> SUPPORTED_LOCALES = List.of(
            Locale.forLanguageTag("zh-CN"),
            Locale.forLanguageTag("en-US"));

    /** 校验已安装模块声明的错误码和 Operation Catalog 节点在每种支持语言都有静态消息。 */
    @Test
    @DisplayName("Resolves Registered Error Codes And Operation Nodes For Every Supported Locale")
    void resolvesRegisteredErrorCodesAndOperationNodesForEverySupportedLocale() {
        MessageCatalog messages = new ClasspathMessageCatalog(
                new PathMatchingResourcePatternResolver(),
                new LocaleCatalog(SUPPORTED_LOCALES, Locale.forLanguageTag("zh-CN")));

        assertMessageKeysPresent(
                messages,
                registeredErrorCodes().stream()
                        .map(ErrorCode::messageKey)
                        .toList());
        assertMessageKeysPresent(
                messages,
                IotFailureCode.builtIns()
                        .stream()
                        .map(IotFailureCode::messageKey)
                        .toList());

        OperationCatalog catalog = new OperationCatalog(
                List.of(
                        new PlatformOperations(),
                        new SystemOperations(),
                        new IotOperations()));
        assertMessageKeysPresent(
                messages,
                catalog.tree()
                        .stream()
                        .flatMap(AppMessageCatalogContractTest::nodes)
                        .map(OperationNode::nameKey)
                        .toList());
    }

    /**
     * 返回默认应用依赖的各模块错误码枚举常量。
     *
     * @return 默认应用对外使用的错误码
     */
    private static List<ErrorCode> registeredErrorCodes() {
        List<ErrorCode> errorCodes = new ArrayList<>();
        Collections.addAll(errorCodes, CoreErrorCode.values());
        Collections.addAll(errorCodes, HttpErrorCode.values());
        Collections.addAll(errorCodes, AuthErrorCode.values());
        Collections.addAll(errorCodes, IamErrorCode.values());
        Collections.addAll(errorCodes, RbacErrorCode.values());
        Collections.addAll(errorCodes, PasswordErrorCode.values());
        Collections.addAll(errorCodes, TotpErrorCode.values());
        Collections.addAll(errorCodes, PasskeyErrorCode.values());
        Collections.addAll(errorCodes, EmailCodeErrorCode.values());
        Collections.addAll(errorCodes, EmailNotificationErrorCode.values());
        Collections.addAll(errorCodes, FileErrorCode.values());
        Collections.addAll(errorCodes, IotErrorCode.values());
        return List.copyOf(errorCodes);
    }

    /**
     * 按深度优先顺序展开一个 Operation 节点及其子节点。
     *
     * @param node 待展开的根节点
     * @return 包含根节点和全部子节点的流
     */
    private static Stream<OperationNode> nodes(OperationNode node) {
        return Stream.concat(Stream.of(node), node.children().stream().flatMap(AppMessageCatalogContractTest::nodes));
    }

    /**
     * 校验每个消息键在默认应用支持的 Locale 中都能解析为非空白文本。
     *
     * @param messages 默认应用的模块消息目录
     * @param keys 待校验的稳定消息键
     */
    private static void assertMessageKeysPresent(MessageCatalog messages, List<String> keys) {
        for (Locale locale : SUPPORTED_LOCALES) {
            for (String key : keys) {
                assertThat(messages.find(key, locale))
                        .as("locale=%s key=%s", locale.toLanguageTag(), key)
                        .hasValueSatisfying(value -> assertThat(value).isNotBlank());
            }
        }
    }
}
