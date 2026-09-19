package com.sca4d.iot.persistence.mapper;

import com.sca4d.core.api.i18n.LocalizedFields;
import com.sca4d.iot.persistence.ProductAccessMethodState;
import com.sca4d.iot.persistence.ProductProtocolState;
import com.sca4d.iot.persistence.ProductState;
import org.apache.ibatis.annotations.Param;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.List;

/** 读写主 PostgreSQL 中的 Product、允许接入方式和兼容协议。 */
public interface ProductMapper {

    /**
     * 新增 Product 标量字段。
     *
     * @param id Product 标识
     * @param code Product 内部编码
     * @param i18n 完整多语言展示字段
     * @return 影响行数
     */
    int insertProduct(
            @Param("id") long id,
            @Param("code") @NotNull String code,
            @Param("i18n") @NotNull LocalizedFields i18n);

    /**
     * 新增 Product 允许的设备接入方式。
     *
     * @param productId Product 标识
     * @param methodCodes 接入方式编码集合
     * @return 影响行数
     */
    int insertProductAccessMethods(
            @Param("productId") long productId,
            @Param("methodCodes") @NotNull Collection<@NotNull String> methodCodes);

    /**
     * 新增 Product 兼容的设备协议。
     *
     * @param productId Product 标识
     * @param protocolCodes 协议编码集合
     * @return 影响行数
     */
    int insertProductProtocols(
            @Param("productId") long productId,
            @Param("protocolCodes") @NotNull Collection<@NotNull String> protocolCodes);

    /** @return Product 总数 */
    long countProducts();

    /** 按可选标识或编码读取 Product，目录窗口可按 code 排序。 */
    @NotNull
    List<ProductState> selectProducts(
            @Param("id") @Nullable Long id,
            @Param("code") @Nullable String code,
            @Param("offset") long offset,
            @Param("size") int size,
            @Param("orderByCode") boolean orderByCode);

    /** @param productIds Product 标识集合 @return 按 Product 标识和编码排列的接入方式 */
    @NotNull
    List<ProductAccessMethodState> selectProductAccessMethodsBatch(
            @Param("productIds") @NotNull Collection<Long> productIds);

    /** @param productIds Product 标识集合 @return 按 Product 标识和编码排列的协议 */
    @NotNull
    List<ProductProtocolState> selectProductProtocolsBatch(
            @Param("productIds") @NotNull Collection<Long> productIds);

    /**
     * 修改 Product 完整多语言展示字段。
     *
     * @param id Product 标识
     * @param i18n 新的完整多语言展示字段
     * @return 影响行数
     */
    int updateProductI18n(
            @Param("id") long id,
            @Param("i18n") @NotNull LocalizedFields i18n);

    /**
     * 推进 Product 主记录更新时间，表示关联的接入方式或协议已经修改。
     *
     * @param id Product 标识
     * @return 影响行数
     */
    int touchProduct(@Param("id") long id);

    /**
     * 删除 Product 当前全部允许接入方式。
     *
     * @param productId Product 标识
     * @return 影响行数
     */
    int deleteProductAccessMethods(@Param("productId") long productId);

    /**
     * 删除 Product 当前全部兼容协议。
     *
     * @param productId Product 标识
     * @return 影响行数
     */
    int deleteProductProtocols(@Param("productId") long productId);
}
