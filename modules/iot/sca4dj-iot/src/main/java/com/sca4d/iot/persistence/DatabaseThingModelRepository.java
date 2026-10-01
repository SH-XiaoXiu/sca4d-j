package com.sca4d.iot.persistence;

import cn.hutool.crypto.digest.DigestUtil;
import com.sca4d.core.api.i18n.LocalizedFields;
import com.sca4d.core.api.persistence.AffectedRows;
import com.sca4d.iot.api.model.ThingModel;
import com.sca4d.iot.api.model.ThingModelPresentation;
import com.sca4d.iot.api.model.ThingModelRepository;
import com.sca4d.iot.model.ThingModelJsonCodec;
import com.sca4d.iot.persistence.mapper.ThingModelMapper;
import org.jetbrains.annotations.CheckReturnValue;
import org.jetbrains.annotations.NotNull;

import java.time.Clock;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/** 使用主 PostgreSQL 保存连续且发布后不可修改的物模型版本。 */
public class DatabaseThingModelRepository implements ThingModelRepository {

    private final @NotNull ThingModelMapper mapper;

    private final @NotNull Clock clock;

    private final @NotNull ThingModelJsonCodec codec;

    /**
     * @param mapper 物模型 SQL Mapper
     * @param codec 规范 JSON 编解码器
     * @param clock 发布时间时钟
     */
    public DatabaseThingModelRepository(
            @NotNull ThingModelMapper mapper,
            @NotNull ThingModelJsonCodec codec,
            @NotNull Clock clock) {
        this.mapper = Objects.requireNonNull(mapper, "物模型 Mapper 不能为空");
        this.codec = Objects.requireNonNull(codec, "物模型 JSON Codec 不能为空");
        this.clock = Objects.requireNonNull(clock, "物模型时钟不能为空");
    }

    @Override
    public @NotNull Optional<ThingModelPresentation> find(@NotNull String productCode, int version) {
        return mapper.selectThingModels(productCode, version)
                .stream()
                .findFirst()
                .map(this::decode);
    }

    @Override
    public @NotNull List<ThingModelPresentation> findPublished(@NotNull String productCode) {
        return mapper.selectThingModels(productCode, null).stream().map(this::decode).toList();
    }

    @Override
    @CheckReturnValue
    @AffectedRows(target = "新增物模型")
    public @NotNull ThingModelPresentation publish(
            @NotNull ThingModel model,
            @NotNull LocalizedFields i18n) {
        ThingModel checked = Objects.requireNonNull(model, "物模型不能为空");
        LocalizedFields checkedI18n = Objects.requireNonNull(i18n, "物模型多语言字段不能为空");
        String json = codec.encode(checked);
        Long productId = mapper.selectProductIdByCode(checked.productCode());
        if (productId == null) {
            throw new IllegalStateException("物模型写入时 Product 不存在");
        }
        mapper.insertThingModel(
                productId,
                checked.version(),
                json,
                DigestUtil.sha256Hex(json),
                checkedI18n,
                clock.instant());
        return ThingModelPresentation.translated(checked, checkedI18n);
    }

    @Override
    @CheckReturnValue
    @AffectedRows(target = "物模型多语言字段")
    public @NotNull ThingModelPresentation replaceI18n(
            @NotNull String productCode,
            int version,
            @NotNull LocalizedFields i18n) {
        String code = Objects.requireNonNull(productCode, "Product code 不能为空");
        LocalizedFields checked = Objects.requireNonNull(i18n, "物模型多语言字段不能为空");
        mapper.updateThingModelI18n(code, version, checked);
        return find(code, version).orElseThrow(() -> new IllegalStateException("修改后的物模型不存在"));
    }

    /** 把数据库规范 JSON 和动态翻译组合成公开读取结果。 */
    private @NotNull ThingModelPresentation decode(@NotNull EncodedThingModel row) {
        EncodedThingModel checked = Objects.requireNonNull(row, "物模型持久化结果不能为空");
        return ThingModelPresentation.translated(codec.decode(checked.modelJson()), checked.i18n());
    }

}
