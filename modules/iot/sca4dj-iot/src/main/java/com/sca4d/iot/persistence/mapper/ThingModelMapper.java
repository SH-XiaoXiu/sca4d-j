package com.sca4d.iot.persistence.mapper;

import com.sca4d.core.api.i18n.LocalizedFields;
import com.sca4d.iot.persistence.EncodedThingModel;
import org.apache.ibatis.annotations.Param;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.time.Instant;
import java.util.List;

/** 读写主 PostgreSQL 中已经发布且不可修改的 ThingModel 版本。 */
public interface ThingModelMapper {

    /**
     * 查询 Product 已发布的全部或指定版本 ThingModel。
     *
     * @param productCode Product 编码
     * @return 按版本递增排列的查询结果
     */
    @NotNull
    List<EncodedThingModel> selectThingModels(
            @Param("productCode") @NotNull String productCode,
            @Param("version") @Nullable Integer version);

    /** 按 Product 编码读取其数据库标识，供物模型保存所属 Product。 */
    @Nullable
    Long selectProductIdByCode(@Param("productCode") @NotNull String productCode);

    /**
     * 新增一个不可修改的 ThingModel 版本。
     *
     * @param productId Product 标识
     * @param version 从 1 开始的新版本
     * @param modelJson 完整模型 JSON
     * @param contentHash 规范 JSON 的内容摘要
     * @param i18n 完整动态翻译
     * @param now 发布时间
     * @return 影响行数
     */
    int insertThingModel(
            @Param("productId") long productId,
            @Param("version") int version,
            @Param("modelJson") @NotNull String modelJson,
            @Param("contentHash") @NotNull String contentHash,
            @Param("i18n") @NotNull LocalizedFields i18n,
            @Param("now") @NotNull Instant now);

    /**
     * 完整替换已发布物模型的动态翻译。
     *
     * @param productCode Product code
     * @param version 物模型版本
     * @param i18n 新的完整动态翻译
     * @return 影响行数
     */
    int updateThingModelI18n(
            @Param("productCode") @NotNull String productCode,
            @Param("version") int version,
            @Param("i18n") @NotNull LocalizedFields i18n);
}
