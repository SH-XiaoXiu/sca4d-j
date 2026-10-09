package com.sca4d.core.api.job;

import com.sca4d.core.api.name.Key;
import com.sca4d.core.api.name.NameFormat;
import com.sca4d.core.api.name.code.Code;
import com.sca4d.core.api.name.code.TreeCode;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.Objects;

/** 标识同一 Job 下参与并发控制的具体工作对象。 */
public final class JobWorkKey extends Key {

    private JobWorkKey(@NotNull Key key) {
        super(key);
        requireLength();
    }

    private JobWorkKey(@NotNull List<String> segments) {
        super(segments);
        requireLength();
    }

    /** 把自由文本作为动态值保存，不把内容误解析为稳定编码。 */
    @Contract(pure = true)
    public static @NotNull JobWorkKey of(@NotNull String value) {
        String checked = Objects.requireNonNull(value, "Job 工作键值不能为空");
        if (checked.isBlank()) {
            throw new IllegalArgumentException("Job 工作键值不能为空白");
        }
        return new JobWorkKey(Key.of(TreeCode.ofSegment("JOB_WORK"), checked));
    }

    /** 使用命名空间和对象标识创建工作键。 */
    @Contract(pure = true)
    public static @NotNull JobWorkKey of(@NotNull Code namespace, @NotNull String value) {
        String checked = Objects.requireNonNull(value, "Job 工作键值不能为空");
        if (checked.isBlank()) {
            throw new IllegalArgumentException("Job 工作键值不能为空白");
        }
        return new JobWorkKey(Key.of(namespace, checked));
    }

    /** 以稳定命名空间和动态数字标识创建工作键。 */
    @Contract(pure = true)
    public static @NotNull JobWorkKey of(@NotNull Code namespace, long value) {
        return new JobWorkKey(Key.of(namespace, value));
    }

    /** 使用 Job 定义标识创建默认固定工作键。 */
    @Contract(pure = true)
    public static @NotNull JobWorkKey fixed(@NotNull JobKey jobKey) {
        return new JobWorkKey(Key.of(Objects.requireNonNull(jobKey, "JobKey 不能为空")));
    }

    /** 返回已编码且不含 App 前缀的数据库工作键。 */
    @Contract(pure = true)
    public @NotNull String value() {
        return NameFormat.COLON.join(segments());
    }

    @Override
    protected @NotNull JobWorkKey withSegments(@NotNull List<String> segments) {
        return new JobWorkKey(segments);
    }

    @Override
    public @NotNull JobWorkKey part(@NotNull String value) {
        return (JobWorkKey) super.part(value);
    }

    @Override
    public @NotNull JobWorkKey part(long value) {
        return (JobWorkKey) super.part(value);
    }

    private void requireLength() {
        if (value().length() > 256) {
            throw new IllegalArgumentException("Job 工作键超过 256 个字符");
        }
    }
}
