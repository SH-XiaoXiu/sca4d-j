package com.sca4d.core.api.identity;

import com.sca4d.core.api.name.code.TreeCode;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Unmodifiable;

import java.util.Map;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicReference;

/** 在数据库提交成功后一次性发布系统身份的不可变运行期常量。 */
public class SystemPrincipals {

    /** 根协调和默认自主执行身份的稳定编码。 */
    public static final @NotNull TreeCode MAIN = TreeCode.ofSegment("MAIN");

    private static final AtomicReference<Map<TreeCode, SystemPrincipal>> VALUES = new AtomicReference<>();

    private SystemPrincipals() {
    }

    /**
     * 在初始化事务已提交后发布完整身份表；重复发布只允许相同内容。
     *
     * @throws IllegalStateException MAIN 缺失、映射不一致或试图改变已发布 ID 时抛出
     */
    public static void publish(@NotNull Map<TreeCode, SystemPrincipal> principals) {
        Map<TreeCode, SystemPrincipal> snapshot = Map.copyOf(Objects.requireNonNull(principals, "系统身份表不能为空"));
        if (!snapshot.containsKey(MAIN)) {
            throw new IllegalStateException("系统身份表必须包含 MAIN");
        }
        snapshot.forEach((code, principal) -> {
            if (!code.equals(principal.code())) {
                throw new IllegalStateException("系统身份编码与 Principal 不一致");
            }
        });
        if (!VALUES.compareAndSet(null, snapshot) && !snapshot.equals(VALUES.get())) {
            throw new IllegalStateException("系统身份常量已经发布；改变 ID 必须人工迁移数据库并重启进程");
        }
    }

    /** 返回已经发布的根系统身份；初始化完成前读取立即失败。 */
    public static @NotNull SystemPrincipal mainPrincipal() {
        return require(MAIN);
    }

    /** 返回指定编码的运行期常量，缺失编码或提前读取都不使用临时默认 ID。 */
    public static @NotNull SystemPrincipal require(@NotNull TreeCode code) {
        SystemPrincipal principal = snapshot().get(Objects.requireNonNull(code, "系统身份编码不能为空"));
        if (principal == null) {
            throw new IllegalStateException("系统身份未配置: " + code);
        }
        return principal;
    }

    /** 返回完整、不可修改且经过数据库确认的身份表。 */
    public static @NotNull @Unmodifiable Map<TreeCode, SystemPrincipal> snapshot() {
        Map<TreeCode, SystemPrincipal> current = VALUES.get();
        if (current == null) {
            throw new IllegalStateException("系统身份尚未完成数据库初始化");
        }
        return current;
    }
}
