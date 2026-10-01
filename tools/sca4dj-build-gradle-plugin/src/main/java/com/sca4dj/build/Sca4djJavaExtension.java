package com.sca4dj.build;

import org.gradle.api.model.ObjectFactory;
import org.gradle.api.provider.ListProperty;

import javax.inject.Inject;

/** 配置当前 Java 项目的字段排序优先级；未配置时使用脚手架规则。 */
public class Sca4djJavaExtension {
    private final ListProperty<String> memberOrderGroups;

    /** 创建供应用项目覆盖的 Java 格式配置。 */
    @Inject
    public Sca4djJavaExtension(ObjectFactory objects) {
        memberOrderGroups = objects.listProperty(String.class)
                .convention(JavaMemberOrderStep.defaultGroups());
    }

    /** @return 从高到低的类型后缀组；组内后缀用竖线分隔 */
    public ListProperty<String> getMemberOrderGroups() {
        return memberOrderGroups;
    }
}
