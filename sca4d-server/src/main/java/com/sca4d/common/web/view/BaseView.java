package com.sca4d.common.web.view;

import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * 响应视图基类。
 *
 * <p>该基类只修改最终响应 JSON 的展示字段，不执行权限、状态或数据查询。普通字段仍由 Jackson 按 Bean 规则序列化；
 * 当视图需要派生字段、字段裁剪、字段脱敏或深层结构调整时，可以通过 {@link #json(Consumer)} 注册 JSON 操作。
 */
public abstract class BaseView {

    private final List<Consumer<ViewJson>> jsonActions = new ArrayList<>();

    /**
     * 注册最终 JSON 操作。
     *
     * <p>操作会在普通 Bean 序列化完成后执行。调用方应只在这里处理展示结构，不应访问数据库、
     * 调用外部服务或写入业务状态。
     *
     * @param action JSON 操作
     */
    protected final void json(@NotNull Consumer<ViewJson> action) {
        jsonActions.add(action);
    }

    /**
     * 最终渲染钩子。
     *
     * <p>子类可以重写该方法集中处理脱敏、裁剪或派生字段。默认不做任何处理。
     *
     * @param json 当前视图 JSON
     */
    protected void render(@NotNull ViewJson json) {
    }

    /**
     * 应用当前视图注册的全部 JSON 操作。
     *
     * <p>该方法由序列化器调用，不建议业务代码直接使用。
     *
     * @param json 当前视图 JSON
     */
    public final void apply(@NotNull ViewJson json) {
        // 按注册顺序执行 from(...) 内追加的 JSON 操作，保证调用方能控制覆盖顺序。
        for (Consumer<ViewJson> action : jsonActions) {
            action.accept(json);
        }
        // 执行子类最终渲染钩子，用于共用脱敏、裁剪等固定规则。
        render(json);
    }
}
