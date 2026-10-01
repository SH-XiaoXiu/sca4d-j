package com.sca4d.iot.model;

import com.sca4d.core.api.i18n.LocalizedFields;
import com.sca4d.iot.api.model.ThingModel;
import com.sca4d.iot.api.model.ThingModelPresentation;
import com.sca4d.iot.api.model.ThingModelRepository;
import org.jetbrains.annotations.NotNull;

import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/** 从 App 静态 Builder 定义构造只读 ThingModelRepository。 */
public class StaticThingModelRepository implements ThingModelRepository {

    private final @NotNull Map<String, Map<Integer, ThingModelPresentation>> models;

    /** @param models App 固定提供的全部物模型 */
    public StaticThingModelRepository(@NotNull Collection<ThingModel> models) {
        this(models, List.of());
    }

    /**
     * 创建同时接受纯技术回退模型和带静态消息展示模型的只读 Repository。
     *
     * @param models 没有静态消息时仅使用 code 展示的模型
     * @param presentations 使用 `MessageText` 声明完整展示字段的模型
     */
    public StaticThingModelRepository(
            @NotNull Collection<ThingModel> models,
            @NotNull Collection<ThingModelPresentation> presentations) {
        LinkedHashMap<String, Map<Integer, ThingModelPresentation>> grouped = new LinkedHashMap<>();
        for (ThingModel model : Objects.requireNonNull(models, "静态物模型不能为空")) {
            ThingModel checked = Objects.requireNonNull(model, "静态物模型不能为空");
            put(grouped, ThingModelPresentation.fallback(checked));
        }
        for (ThingModelPresentation presentation : Objects.requireNonNull(
                presentations,
                "静态物模型展示定义不能为空")) {
            put(grouped, Objects.requireNonNull(presentation, "静态物模型展示定义不能为空"));
        }
        this.models = Map.copyOf(grouped);
    }

    /** 把一个静态模型加入 Product 与版本索引并拒绝重复。 */
    private static void put(
            Map<String, Map<Integer, ThingModelPresentation>> grouped,
            ThingModelPresentation presentation) {
        ThingModel checked = presentation.model();
        Map<Integer, ThingModelPresentation> versions = new LinkedHashMap<>(
                grouped.getOrDefault(checked.productCode(), Map.of()));
        if (versions.putIfAbsent(checked.version(), presentation) != null) {
            throw new IllegalArgumentException("静态物模型版本重复");
        }
        grouped.put(checked.productCode(), Map.copyOf(versions));
    }

    @Override
    public @NotNull Optional<ThingModelPresentation> find(@NotNull String productCode, int version) {
        return Optional.ofNullable(models.getOrDefault(productCode, Map.of()).get(version));
    }

    @Override
    public @NotNull List<ThingModelPresentation> findPublished(@NotNull String productCode) {
        return models.getOrDefault(productCode, Map.of())
                .values()
                .stream()
                .sorted(Comparator.comparingInt(value -> value.model().version()))
                .toList();
    }

    @Override
    public @NotNull ThingModelPresentation publish(
            @NotNull ThingModel model,
            @NotNull LocalizedFields i18n) {
        throw new UnsupportedOperationException("静态物模型 Repository 不支持运行时发布");
    }

    @Override
    public @NotNull ThingModelPresentation replaceI18n(
            @NotNull String productCode,
            int version,
            @NotNull LocalizedFields i18n) {
        throw new UnsupportedOperationException("静态物模型 Repository 不支持运行时修改翻译");
    }
}
