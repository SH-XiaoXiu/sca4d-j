package com.sca4dj.build;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

/** 验证字段排序只移动安全的声明，不改写构造器或注释归属。 */
class JavaMemberOrderStepTest {
    @Test
    @DisplayName("默认优先级按类型排序依赖并保留构造器签名")
    void sortsConstructorDependencies() {
        String source = """
                class Sample {
                    private final @NotNull ZRepository z;

                    /** A 服务说明。 */
                    private final @NotNull AService a;

                    private final @NotNull BService b;

                    private final @NotNull ARepository repository;

                    Sample(ZRepository z, AService a, BService b, ARepository repository) {
                        this.z = z;
                        this.a = a;
                        this.b = b;
                        this.repository = repository;
                    }
                }
                """;

        String result = JavaMemberOrderStep.format(source, JavaMemberOrderStep.defaultGroups());

        assertTrue(result.indexOf("/** A 服务说明。 */") < result.indexOf("AService a;"));
        assertTrue(result.indexOf("AService a;") < result.indexOf("BService b;"));
        assertTrue(result.indexOf("BService b;") < result.indexOf("ARepository repository;"));
        assertTrue(result.indexOf("ARepository repository;") < result.indexOf("ZRepository z;"));
        assertTrue(result.contains("Sample(ZRepository z, AService a, BService b, ARepository repository)"));
        assertEquals(result, JavaMemberOrderStep.format(result, JavaMemberOrderStep.defaultGroups()));
    }

    @Test
    @DisplayName("多行 Javadoc 随字段移动，独立分节注释阻止跨节排序")
    void keepsCommentsWithTheirFields() {
        String source = """
                class Sample {
                    private final ZRepository repository;

                    /**
                     * A 服务说明。
                     */
                    private final AService service;

                    // 独立分节

                    private final BService later;

                    Sample(ZRepository repository, AService service, BService later) {
                        this.repository = repository;
                        this.service = service;
                        this.later = later;
                    }
                }
                """;

        String result = JavaMemberOrderStep.format(source, JavaMemberOrderStep.defaultGroups());

        assertTrue(result.indexOf("A 服务说明") < result.indexOf("AService service;"));
        assertTrue(result.indexOf("AService service;") < result.indexOf("ZRepository repository;"));
        assertTrue(result.indexOf("ZRepository repository;") < result.indexOf("// 独立分节"));
        assertTrue(result.indexOf("// 独立分节") < result.indexOf("BService later;"));
        assertEquals(result, JavaMemberOrderStep.format(result, JavaMemberOrderStep.defaultGroups()));
    }

    @Test
    @DisplayName("应用替换后缀优先级并保留同组类型名顺序")
    void appliesAppGroups() {
        String source = """
                class Sample {
                    private final ZService service;
                    private final BRepository second;
                    private final ARepository first;
                    Sample(ZService service, BRepository second, ARepository first) {
                        this.service = service;
                        this.second = second;
                        this.first = first;
                    }
                }
                """;

        String result = JavaMemberOrderStep.format(source, List.of("Repository|Repositories", "Service|Services"));

        assertTrue(result.indexOf("ARepository first;") < result.indexOf("BRepository second;"));
        assertTrue(result.indexOf("BRepository second;") < result.indexOf("ZService service;"));
    }

    @Test
    @DisplayName("字面量常量按名称排序而运行时初始化保持原序")
    void keepsRuntimeInitializationOrder() {
        String source = """
                class Sample {
                    private static final EventType EVENT = new EventType();
                    private static final Logger LOG = LoggerFactory.getLogger(Sample.class);
                    private static final String Z_VALUE = "z";
                    private static final String A_VALUE = "a";
                }
                """;

        String result = JavaMemberOrderStep.format(source, JavaMemberOrderStep.defaultGroups());

        assertTrue(result.indexOf("A_VALUE") < result.indexOf("Z_VALUE"));
        assertTrue(result.indexOf("EVENT") < result.indexOf("Logger LOG"));
        assertEquals(result, JavaMemberOrderStep.format(result, JavaMemberOrderStep.defaultGroups()));
    }

    @Test
    @DisplayName("状态字段和 Lombok 构造器字段不自动移动")
    void skipsStateAndGeneratedConstructors() {
        String source = """
                @RequiredArgsConstructor
                class Generated {
                    private final ZRepository repository;
                    private final AService service;
                }
                class Stateful {
                    private final ZRepository repository;
                    private int count;
                    private final AService service;
                    Stateful(ZRepository repository, AService service) {
                        this.repository = repository;
                        this.service = service;
                    }
                }
                """;

        assertEquals(source, JavaMemberOrderStep.format(source, JavaMemberOrderStep.defaultGroups()));
    }

    @Test
    @DisplayName("重复或空白后缀使配置失败")
    void rejectsInvalidGroups() {
        assertThrows(
                IllegalArgumentException.class,
                () -> JavaMemberOrderStep.create(List.of("Service|Services", "Service")));
        assertThrows(
                IllegalArgumentException.class,
                () -> JavaMemberOrderStep.create(List.of("Service|")));
    }
}
