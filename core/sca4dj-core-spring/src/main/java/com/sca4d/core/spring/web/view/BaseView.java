package com.sca4d.core.spring.web.view;

import com.sca4d.core.api.i18n.LocalizedFields;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;

/** 在普通 Bean 序列化完成后定制最终 JSON 的 HTTP View 基类。 */
public abstract class BaseView {

    private final List<Consumer<ViewJson>> jsonActions = new ArrayList<>();

    private final @Nullable LocalizedFields localizedFields;

    /** 供具体 View 继承。 */
    protected BaseView() {
        localizedFields = null;
    }

    /**
     * 供包含动态国际化字段的具体 View 继承。
     *
     * @param localizedFields 当前业务行随查询结果返回的多语言字段
     */
    protected BaseView(@NotNull LocalizedFields localizedFields) {
        this.localizedFields = Objects.requireNonNull(localizedFields, "View 多语言字段不能为空");
    }

    /**
     * 登记一次最终 JSON 操作。
     *
     * @param action 只修改展示 JSON、不访问外部状态的操作
     */
    protected final void json(@NotNull Consumer<ViewJson> action) {
        jsonActions.add(Objects.requireNonNull(action, "ViewJson 操作不能为空"));
    }

    /**
     * 最终渲染钩子，默认不修改 JSON。
     *
     * @param json 当前最终 JSON
     */
    protected void render(@NotNull ViewJson json) {
    }

    /**
     * 按登记顺序执行全部 action，最后调用 render。
     *
     * @param json 当前最终 JSON
     */
    public final void apply(@NotNull ViewJson json) {
        Objects.requireNonNull(json, "ViewJson 不能为空");
        jsonActions.forEach(action -> action.accept(json));
        render(json);
    }

    /** 返回动态 `@Localize` 使用的多语言字段；普通 View 没有该数据。 */
    final @Nullable LocalizedFields localizedFields() {
        return localizedFields;
    }
}
