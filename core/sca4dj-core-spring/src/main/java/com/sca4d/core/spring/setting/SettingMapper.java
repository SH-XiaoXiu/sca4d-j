package com.sca4d.core.spring.setting;

import com.sca4d.core.api.name.code.TreeCode;
import com.sca4d.core.api.persistence.Patch;
import org.apache.ibatis.annotations.Param;
import org.jetbrains.annotations.CheckReturnValue;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.List;

/** 定义 `sys_setting` 表的查询、条件写入和初始化锁 SQL。 */
public interface SettingMapper {
    /** 查询规范编码及可能冲突的旧编码，旧值只用于阻止静默采用默认值。 */
    @NotNull
    List<StoredSetting> selectCanonicalOrLegacy(
            @Param("ownerType") TreeCode ownerType,
            @Param("ownerId") String ownerId,
            @Param("key") String key);

    /** @return 指定 owner 的非空键集合对应设置行 */
    @CheckReturnValue
    @NotNull
    List<@NotNull StoredSetting> select(
            @Param("ownerType") @NotNull TreeCode ownerType,
            @Param("ownerId") @NotNull String ownerId,
            @Param("keys") @NotNull Collection<@NotNull String> keys);

    /**
     * 写入初始化节点持续同步的 SystemSetting，并禁止管理端修改。
     *
     * @return 新增或修改行数
     */
    @CheckReturnValue
    int synchronizeSystem(
            @Param("ownerType") @NotNull TreeCode ownerType,
            @Param("ownerId") @NotNull String ownerId,
            @Param("key") @NotNull String key,
            @Param("schemaVersion") int schemaVersion,
            @Param("valueText") @NotNull Patch<String> valueText,
            @Param("valueKeyId") @NotNull Patch<String> valueKeyId,
            @Param("valueCiphertext") @NotNull Patch<byte[]> valueCiphertext,
            @Param("encryptionContextVersion") @NotNull Patch<Integer> encryptionContextVersion);

    /** @return 解除 SystemSetting ENV 同步状态的修改行数 */
    @CheckReturnValue
    int releaseSystemSynchronization(
            @Param("ownerType") @NotNull TreeCode ownerType,
            @Param("ownerId") @NotNull String ownerId,
            @Param("key") @NotNull String key);

    /** @return 预期版本为零且 owner 设置行不存在时新增的行数 */
    @CheckReturnValue
    int insertManaged(
            @Param("ownerType") @NotNull TreeCode ownerType,
            @Param("ownerId") @NotNull String ownerId,
            @Param("key") @NotNull String key,
            @Param("schemaVersion") int schemaVersion,
            @Param("valueText") @Nullable String valueText,
            @Param("valueKeyId") @Nullable String valueKeyId,
            @Param("valueCiphertext") byte @Nullable [] valueCiphertext,
            @Param("encryptionContextVersion") @Nullable Integer encryptionContextVersion);

    /** @return owner 行版本匹配且不受 ENV 同步时修改的行数 */
    @CheckReturnValue
    int updateManaged(
            @Param("ownerType") @NotNull TreeCode ownerType,
            @Param("ownerId") @NotNull String ownerId,
            @Param("key") @NotNull String key,
            @Param("schemaVersion") @NotNull Patch<Integer> schemaVersion,
            @Param("hasValue") @NotNull Patch<Boolean> hasValue,
            @Param("valueText") @NotNull Patch<String> valueText,
            @Param("valueKeyId") @NotNull Patch<String> valueKeyId,
            @Param("valueCiphertext") @NotNull Patch<byte[]> valueCiphertext,
            @Param("encryptionContextVersion") @NotNull Patch<Integer> encryptionContextVersion,
            @Param("expectedVersion") long expectedVersion);

    /** @return 物理删除的指定 owner 设置行数 */
    int erase(
            @Param("ownerType") @NotNull TreeCode ownerType,
            @Param("ownerId") @NotNull String ownerId,
            @Param("key") @NotNull String key);

    /** @return 当前事务取得设置初始化锁时返回 {@code true} */
    @CheckReturnValue
    boolean tryLockBootstrap();
}
