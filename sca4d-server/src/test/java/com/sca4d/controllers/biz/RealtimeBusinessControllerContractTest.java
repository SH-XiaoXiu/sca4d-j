package com.sca4d.controllers.biz;

import static org.assertj.core.api.Assertions.assertThat;

import com.sca4d.common.auth.Principal;
import com.sca4d.common.auth.Subject;
import com.sca4d.common.permission.PermissionCatalog;
import com.sca4d.common.permission.PermissionItem;
import com.sca4d.common.permission.PermissionType;
import com.sca4d.common.permission.annotation.RequirePermission;
import com.sca4d.common.realtime.RealtimePermissions;
import com.sca4d.controllers.RealtimeController;
import com.sca4d.controllers.biz.im.ImController;
import com.sca4d.controllers.biz.im.ImStompController;
import com.sca4d.controllers.biz.notification.NotificationController;
import com.sca4d.controllers.mg.system.SystemOperationLogController;
import com.sca4d.im.ImPermissions;
import com.sca4d.im.request.ImConversationCreateRequest;
import com.sca4d.im.request.ImMessageCreateRequest;
import com.sca4d.notification.NotificationPermissions;
import com.sca4d.system.SystemPermissions;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.junit.jupiter.api.Test;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;

import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/** 验证业务实时通信接口的路由、认证和请求参数。 */
class RealtimeBusinessControllerContractTest {

    @Test
    void imHttpControllerShouldUseConversationMessageAndReadStatusResources() throws Exception {
        RequestMapping mapping = ImController.class.getAnnotation(RequestMapping.class);
        Map<String, String> permissions = permissions(ImController.class);
        PostMapping createConversation = ImController.class
                .getDeclaredMethod(
                        "createConversation",
                        ImConversationCreateRequest.class,
                        Subject.class,
                        Principal.class)
                .getAnnotation(PostMapping.class);
        PostMapping createMessage = ImController.class
                .getDeclaredMethod(
                        "createMessage",
                        Long.class,
                        ImMessageCreateRequest.class,
                        Subject.class,
                        Principal.class)
                .getAnnotation(PostMapping.class);
        PutMapping readStatus = ImController.class
                .getDeclaredMethod("updateReadStatus", Long.class, Principal.class)
                .getAnnotation(PutMapping.class);
        DeleteMapping deleteMessage = ImController.class
                .getDeclaredMethod("deleteMessage", Long.class, Principal.class)
                .getAnnotation(DeleteMapping.class);

        assertThat(mapping.value()).containsExactly("/im");
        assertThat(createConversation.value()).containsExactly("/conversations");
        assertThat(createMessage.value()).containsExactly("/conversations/{conversationId}/messages");
        assertThat(readStatus.value()).containsExactly("/messages/{messageId}/read-status");
        assertThat(deleteMessage.value()).containsExactly("/messages/{messageId}");
        assertThat(permissions)
                .containsEntry("conversations", ImPermissions.CONVERSATIONS_READ)
                .containsEntry("createConversation", ImPermissions.CONVERSATIONS_CREATE)
                .containsEntry("messages", ImPermissions.MESSAGES_READ)
                .containsEntry("createMessage", ImPermissions.MESSAGES_CREATE)
                .containsEntry("updateReadStatus", ImPermissions.MESSAGES_UPDATE)
                .containsEntry("deleteMessage", ImPermissions.MESSAGES_UPDATE);
    }

    @Test
    void imStompControllerShouldUseMessageResourcePermissions() {
        Map<String, String> permissions = permissions(ImStompController.class);
        Map<String, String[]> mappings = Arrays.stream(ImStompController.class.getDeclaredMethods())
                .filter(method -> method.isAnnotationPresent(MessageMapping.class))
                .collect(Collectors.toMap(Method::getName, method -> method.getAnnotation(MessageMapping.class).value()));

        assertThat(mappings.get("createMessage")).containsExactly("/im/conversations/{conversationId}/messages");
        assertThat(mappings.get("updateReadStatus")).containsExactly("/im/messages/{messageId}/read-status");
        assertThat(permissions)
                .containsEntry("createMessage", ImPermissions.MESSAGES_CREATE)
                .containsEntry("updateReadStatus", ImPermissions.MESSAGES_UPDATE);
    }

    @Test
    void notificationControllerShouldUseReadStatusResources() throws Exception {
        RequestMapping mapping = NotificationController.class.getAnnotation(RequestMapping.class);
        Map<String, String> permissions = permissions(NotificationController.class);
        PutMapping one = NotificationController.class
                .getDeclaredMethod(
                        "updateReadStatus",
                        Long.class,
                        Subject.class,
                        Principal.class)
                .getAnnotation(PutMapping.class);
        PutMapping all = NotificationController.class
                .getDeclaredMethod(
                        "updateAllReadStatus",
                        Subject.class,
                        Principal.class)
                .getAnnotation(PutMapping.class);

        assertThat(mapping.value()).containsExactly("/notifications");
        assertThat(one.value()).containsExactly("/{notificationId}/read-status");
        assertThat(all.value()).containsExactly("/read-status");
        assertThat(permissions)
                .containsEntry("page", NotificationPermissions.MESSAGES_READ)
                .containsEntry("unreadCount", NotificationPermissions.MESSAGES_READ)
                .containsEntry("updateReadStatus", NotificationPermissions.MESSAGES_UPDATE)
                .containsEntry("updateAllReadStatus", NotificationPermissions.MESSAGES_UPDATE);
    }

    @Test
    void lowLevelRealtimeControllerShouldKeepRealtimePermissions() {
        Map<String, String> permissions = permissions(RealtimeController.class);

        assertThat(permissions)
                .containsEntry("subscription", RealtimePermissions.SUBSCRIPTION_READ)
                .containsEntry("sse", RealtimePermissions.SSE_CONNECT);
    }

    @Test
    void activeControllerPermissionsShouldExistInCatalog() {
        PermissionCatalog catalog = new PermissionCatalog(List.of(
                new SystemPermissions(),
                new RealtimePermissions(),
                new ImPermissions(),
                new NotificationPermissions()));
        Set<String> actionCodes = catalog.items().stream()
                .filter(item -> item.getType() == PermissionType.ACTION)
                .map(PermissionItem::getCode)
                .collect(Collectors.toCollection(LinkedHashSet::new));

        Set<String> controllerCodes = permissionCodes(List.of(
                RealtimeController.class,
                ImController.class,
                ImStompController.class,
                NotificationController.class,
                SystemOperationLogController.class));

        assertThat(actionCodes).containsAll(controllerCodes);
    }

    /**
     * 读取 Controller 每个方法声明的首个功能权限编码。
     *
     * <p>每个声明方法只执行一次内存反射检查，不触发 Controller 或外部调用。
     *
     * @param type 待检查 Controller 类型
     * @return 方法名到权限编码的映射
     */
    private @NotNull Map<String, String> permissions(@NotNull Class<?> type) {
        return Arrays.stream(type.getDeclaredMethods())
                .filter(method -> method.isAnnotationPresent(RequirePermission.class))
                .collect(Collectors.toMap(Method::getName, method -> permissionCode(method.getAnnotation(RequirePermission.class))));
    }

    /**
     * 收集 Controller 类级和方法级声明的全部非空权限编码。
     *
     * <p>每个 Controller 执行一次类级反射读取，每个声明方法执行一次方法级反射读取，不触发外部调用。
     *
     * @param types 待检查 Controller 类型集合
     * @return 保持首次出现顺序的权限编码集合
     */
    private @NotNull Set<String> permissionCodes(@NotNull List<Class<?>> types) {
        Set<String> codes = new LinkedHashSet<>();
        for (Class<?> type : types) {
            RequirePermission classPermission = type.getAnnotation(RequirePermission.class);
            String classPermissionCode = permissionCode(classPermission);
            if (!classPermissionCode.isBlank()) {
                codes.add(classPermissionCode);
            }
            for (Method method : type.getDeclaredMethods()) {
                RequirePermission methodPermission = method.getAnnotation(RequirePermission.class);
                String methodPermissionCode = permissionCode(methodPermission);
                if (!methodPermissionCode.isBlank()) {
                    codes.add(methodPermissionCode);
                }
            }
        }
        return codes;
    }

    /**
     * 兼容注解 value 别名并读取首个权限编码。
     *
     * @param permission 可选权限声明
     * @return 未声明权限时返回空字符串，否则返回首个权限编码
     */
    private @NotNull String permissionCode(@Nullable RequirePermission permission) {
        if (permission == null) {
            return "";
        }
        String[] codes = permission.value().length == 0
                ? permission.permissions()
                : permission.value();
        return codes.length == 0 ? "" : codes[0];
    }
}
