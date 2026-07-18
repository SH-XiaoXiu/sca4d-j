package com.sca4d.file.signature;

import com.sca4d.common.exception.BizException;
import com.sca4d.file.error.FileErrorCode;
import org.jetbrains.annotations.NotNull;
import org.springframework.util.StringUtils;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

/** 文件签名场景注册表。 */
public class FileSignatureRegistry {

    private static final Pattern SCENE_PATTERN = Pattern.compile(FileSignatureItem.SCENE_REGEXP);

    private final Map<String, FileSignaturePolicy> policies = new LinkedHashMap<>();

    /**
     * 注册全部业务模块文件签名场景，并在启动时拒绝格式错误或重复编码。
     *
     * @param policies 当前应用提供的全部文件签名策略
     */
    public FileSignatureRegistry(@NotNull List<FileSignaturePolicy> policies) {
        // 启动阶段按策略声明的场景总数线性扫描一次，不执行数据库或外部调用；任一无效或重复编码都会阻止应用启动。
        for (FileSignaturePolicy policy : policies) {
            for (String scene : policy.scenes()) {
                if (!StringUtils.hasText(scene)
                        || scene.length() > FileSignatureItem.SCENE_MAX_LENGTH
                        || !SCENE_PATTERN.matcher(scene).matches()) {
                    throw new IllegalStateException("文件签名场景格式错误: " + scene);
                }
                FileSignaturePolicy existing = this.policies.putIfAbsent(scene, policy);
                if (existing != null) {
                    throw new IllegalStateException("重复文件签名场景: " + scene);
                }
            }
        }
    }

    /**
     * 按固定场景编码读取唯一业务策略。
     *
     * @param scene 业务请求声明的文件签名场景
     * @return 当前应用注册的场景策略
     * @throws BizException 场景未注册时失败关闭
     */
    public @NotNull FileSignaturePolicy require(@NotNull String scene) {
        FileSignaturePolicy policy = policies.get(scene);
        if (policy == null) {
            throw new BizException(FileErrorCode.FILE_SIGNATURE_SCENE_UNKNOWN);
        }
        return policy;
    }
}
