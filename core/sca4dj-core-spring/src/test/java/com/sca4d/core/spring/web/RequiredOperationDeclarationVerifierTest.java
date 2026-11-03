package com.sca4d.core.spring.web;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.sca4d.core.api.annotation.RequirePermission;
import com.sca4d.core.api.name.Display;
import com.sca4d.core.api.name.code.TreeCode;
import com.sca4d.core.api.permission.Action;
import com.sca4d.core.api.permission.OperationCatalog;
import com.sca4d.core.api.permission.OperationModule;
import com.sca4d.core.api.permission.OperationNode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.mvc.method.RequestMappingInfo;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** 验证应用启动时检查 Controller 权限注解声明的固定 Operation。 */
class RequiredOperationDeclarationVerifierTest {

    private static final OperationModule ORDERS = () -> OperationNode.root(
            TreeCode.ofSegment("ORDERS"),
            Display.of("operation.orders"),
            10,
            OperationNode.group(
                    TreeCode.ofSegment("INVOICES"),
                    Display.of("operation.orders.invoices"),
                    10,
                    OperationNode.leaf(Action.READ, Display.of("operation.orders.invoices.read"), 10),
                    OperationNode.leaf(Action.CREATE, Display.of("operation.orders.invoices.create"), 20)));

    private final OperationCatalog catalog = new OperationCatalog(List.of(ORDERS), List.of());

    @Test
    @DisplayName("目录中存在的固定 Operation 和动态资源声明通过启动校验")
    void acceptsDeclaredOperationsAndIgnoresDynamicResources() throws Exception {
        RequiredOperationDeclarationVerifier verifier = verifier(
                handler(new ValidController(), "read"),
                handler(new ValidController(), "readAndCreate"),
                handler(new ValidController(), "dynamic"));

        assertThatCode(verifier::afterSingletonsInstantiated).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("未登记和格式错误的 Operation 在启动时失败并列出位置与文本")
    void rejectsUnknownAndMalformedOperationsWithHandlerLocation() throws Exception {
        RequiredOperationDeclarationVerifier verifier = verifier(
                handler(new InvalidController(), "unknown"),
                handler(new InvalidController(), "malformed"),
                handler(new InvalidController(), "partlyUnknown"));

        assertThatThrownBy(verifier::afterSingletonsInstantiated)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("count=4")
                .hasMessageContaining(InvalidController.class.getName() + " -> ORDERS:INVOICES:ARCHIVE")
                .hasMessageContaining(InvalidController.class.getName() + "#unknown -> ORDERS:INVOICES:DELETE")
                .hasMessageContaining(InvalidController.class.getName() + "#malformed -> ORDERS")
                .hasMessageContaining(InvalidController.class.getName() + "#partlyUnknown -> ORDERS:INVOICES:EXPORT")
                .hasMessageNotContaining("ORDERS:INVOICES:READ");
    }

    private RequiredOperationDeclarationVerifier verifier(HandlerMethod... handlers) {
        RequestMappingHandlerMapping mappings = mock(RequestMappingHandlerMapping.class);
        Map<RequestMappingInfo, HandlerMethod> registered = new LinkedHashMap<>();
        for (int index = 0; index < handlers.length; index++) {
            registered.put(RequestMappingInfo.paths("/" + index).build(), handlers[index]);
        }
        when(mappings.getHandlerMethods()).thenReturn(registered);
        return new RequiredOperationDeclarationVerifier(mappings, catalog);
    }

    private static HandlerMethod handler(Object controller, String method) throws NoSuchMethodException {
        return new HandlerMethod(controller, controller.getClass().getDeclaredMethod(method));
    }

    /** 声明目录中都存在的权限。 */
    static class ValidController {

        /** 单个固定 Operation。 */
        @RequirePermission(operation = "ORDERS:INVOICES:READ")
        public void read() {
        }

        /** 重复注解中的每一项都登记在目录中。 */
        @RequirePermission(operation = "ORDERS:INVOICES:READ")
        @RequirePermission(operation = "orders:invoices:create")
        public void readAndCreate() {
        }

        /** 动态资源声明没有固定 Operation，不参与检查。 */
        @RequirePermission(resource = "#id", resourceType = "ORDERS/INVOICES", action = "READ")
        public void dynamic() {
        }
    }

    /** 声明目录中缺失或格式错误的权限。 */
    @RequirePermission(operation = "ORDERS:INVOICES:ARCHIVE")
    static class InvalidController {

        /** 目录没有登记该 Operation。 */
        @RequirePermission(operation = "ORDERS:INVOICES:DELETE")
        public void unknown() {
        }

        /** 文本少于两个节点，不是合法 Operation 编码。 */
        @RequirePermission(operation = "ORDERS")
        public void malformed() {
        }

        /** 只有一项缺失，已登记的另一项不出现在失败消息中。 */
        @RequirePermission(operation = "ORDERS:INVOICES:READ")
        @RequirePermission(operation = "ORDERS:INVOICES:EXPORT")
        public void partlyUnknown() {
        }
    }
}
