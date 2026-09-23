package com.sca4d.core.api.notification;

import com.sca4d.core.api.code.TreeCode;
import org.jetbrains.annotations.CheckReturnValue;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;

import java.util.Objects;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

/**
 * 把一种领域通知的类型、渠道声明、模板变量和接收人回调组成稳定契约。
 */
public final class NotificationType<T> {

    private final @NotNull TreeCode code;

    private final @NotNull Class<T> valueType;

    private final @NotNull NotificationDefinition<T> definition;

    private final @NotNull BiConsumer<T, NotificationExecution> handler;

    private NotificationType(
            @NotNull String code,
            @NotNull Class<T> valueType,
            @NotNull Consumer<NotificationDefinition<T>> declaration,
            @NotNull BiConsumer<T, NotificationExecution> handler) {
        TreeCode checkedCode = TreeCode.of(code);
        if (checkedCode.hasNode(TreeCode.ofSegment("*"))) {
            throw new IllegalArgumentException("通知编码不能包含通配符节点");
        }
        this.code = checkedCode;
        this.valueType = Objects.requireNonNull(valueType, "领域通知类型不能为空");
        this.definition = new NotificationDefinition<>();
        Objects.requireNonNull(declaration, "通知定义回调不能为空").accept(definition);
        definition.freeze();
        this.handler = Objects.requireNonNull(handler, "通知处理回调不能为空");
    }

    /**
     * 创建一种在应用启动时完成声明的领域通知类型。
     */
    @CheckReturnValue
    @Contract("_, _, _, _ -> new")
    public static <T> @NotNull NotificationType<T> define(
            @NotNull String code,
            @NotNull Class<T> valueType,
            @NotNull Consumer<NotificationDefinition<T>> declaration,
            @NotNull BiConsumer<T, NotificationExecution> handler) {
        return new NotificationType<>(code, valueType, declaration, handler);
    }

    /**
     * @return 跨模板、消息和渠道保持稳定的内部通知编码
     */
    public @NotNull TreeCode code() {
        return code;
    }

    /**
     * @return 业务调用方提交的领域通知类型
     */
    public @NotNull Class<T> valueType() {
        return valueType;
    }

    /**
     * @return 已冻结的渠道和模板变量声明
     */
    public @NotNull NotificationDefinition<T> definition() {
        return definition;
    }

    /**
     * 由 Notification 框架调用类型回调确定接收人、Locale 和区域。
     */
    public void execute(@NotNull T value, @NotNull NotificationExecution execution) {
        T checked = Objects.requireNonNull(value, "领域通知不能为空");
        if (!valueType.isInstance(checked)) {
            throw new IllegalArgumentException("领域通知与 NotificationType 声明不一致");
        }
        handler.accept(checked, Objects.requireNonNull(execution, "通知执行上下文不能为空"));
    }
}
