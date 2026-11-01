package com.aichangeguard.testing.service;

import com.aichangeguard.analysis.model.AnnotationMetadata;
import com.aichangeguard.analysis.model.FieldMetadata;
import com.aichangeguard.analysis.model.MethodInvocationMetadata;
import com.aichangeguard.analysis.model.MethodMetadata;
import com.aichangeguard.analysis.model.ParsedFileMetadata;
import com.aichangeguard.analysis.model.SourceAnalysisReport;
import com.aichangeguard.analysis.model.SourceRange;
import com.aichangeguard.analysis.model.TypeKind;
import com.aichangeguard.analysis.model.TypeMetadata;
import com.aichangeguard.testing.model.TestCase;
import com.aichangeguard.testing.model.TestClass;
import com.aichangeguard.testing.model.UnresolvedTestMapping;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class TestDiscoveryServiceTest {

    private TestDiscoveryService discoveryService;

    @BeforeEach
    void setUp() {
        discoveryService = new TestDiscoveryService();
    }

    private AnnotationMetadata annotation(String name) {
        return new AnnotationMetadata(name, Collections.emptyMap(), SourceRange.UNKNOWN);
    }

    @Test
    @DisplayName("Should discover JUnit 5 test methods and parameterized tests")
    void shouldDiscoverJUnit5TestMethods() {
        MethodInvocationMetadata inv = MethodInvocationMetadata.syntactic(
                "userService", "findById", 1, SourceRange.UNKNOWN
        );

        MethodMetadata testMethod1 = new MethodMetadata(
                "com.example.UserServiceTest#testFind()",
                "testFind", "void", Collections.emptyList(), Set.of("public"),
                List.of(annotation("Test")), Collections.emptyList(), false, false,
                SourceRange.of(15, 1, 20, 1), List.of(inv)
        );

        MethodMetadata testMethod2 = new MethodMetadata(
                "com.example.UserServiceTest#testInvalid(String)",
                "testInvalid", "void", Collections.emptyList(), Set.of("public"),
                List.of(annotation("ParameterizedTest")), Collections.emptyList(), false, false,
                SourceRange.of(25, 1, 30, 1), Collections.emptyList()
        );

        MethodMetadata helperMethod = new MethodMetadata(
                "com.example.UserServiceTest#setupMock()",
                "setupMock", "void", Collections.emptyList(), Set.of("private"),
                Collections.emptyList(), Collections.emptyList(), false, false,
                SourceRange.of(35, 1, 40, 1), Collections.emptyList()
        );

        FieldMetadata field = new FieldMetadata(
                "com.example.UserServiceTest#userService",
                "userService", "UserService", Set.of("private"),
                List.of(annotation("Mock")), SourceRange.UNKNOWN
        );

        TypeMetadata testClassType = new TypeMetadata(
                "com.example.UserServiceTest", "com.example.UserServiceTest", "UserServiceTest",
                TypeKind.CLASS, Set.of("public"),
                List.of(annotation("ExtendWith")),
                Collections.emptyList(), Collections.emptyList(),
                List.of(field), Collections.emptyList(),
                List.of(testMethod1, testMethod2, helperMethod),
                Collections.emptyList(),
                SourceRange.of(1, 1, 50, 1)
        );

        ParsedFileMetadata file = new ParsedFileMetadata(
                "src/test/java/com/example/UserServiceTest.java",
                "com.example",
                List.of("com.example.UserService", "org.junit.jupiter.api.Test"),
                List.of(testClassType),
                Collections.emptyList(),
                true,
                SourceRange.UNKNOWN
        );

        SourceAnalysisReport report = new SourceAnalysisReport(
                "/repo", 1, 1, 0, 100.0,
                List.of(file), Collections.emptyList(),
                Instant.now(), Collections.emptyList()
        );

        List<TestClass> testClasses = discoveryService.discoverTestClasses(report);

        assertThat(testClasses).hasSize(1);
        TestClass tc = testClasses.getFirst();
        assertThat(tc.simpleName()).isEqualTo("UserServiceTest");
        assertThat(tc.testMethods()).hasSize(2); // Only @Test and @ParameterizedTest, helper ignored

        TestCase tm1 = tc.testMethods().getFirst();
        assertThat(tm1.testMethodName()).isEqualTo("testFind");
        assertThat(tm1.isParameterized()).isFalse();
        assertThat(tm1.referencedProductionSymbols()).contains("findById", "userService.findById");
        assertThat(tm1.referencedProductionTypes()).contains("UserService");

        TestCase tm2 = tc.testMethods().get(1);
        assertThat(tm2.testMethodName()).isEqualTo("testInvalid");
        assertThat(tm2.isParameterized()).isTrue();
    }

    @Test
    @DisplayName("Should identify unresolved test mappings")
    void shouldIdentifyUnresolvedMappings() {
        TestCase tm = new TestCase(
                "com.example.SimpleTest", "SimpleTest", "testIt",
                "com.example.SimpleTest#testIt()", "SimpleTest.java", 10,
                List.of("Test"), false, Set.of("unqualifiedMethodCall"), Set.of()
        );

        TestClass tc = new TestClass(
                "com.example.SimpleTest", "SimpleTest", "SimpleTest.java", 1,
                List.of(), List.of(tm), Set.of()
        );

        List<UnresolvedTestMapping> unresolved = discoveryService.findUnresolvedMappings(List.of(tc));

        assertThat(unresolved).hasSize(1);
        assertThat(unresolved.getFirst().targetInvocation()).isEqualTo("unqualifiedMethodCall");
        assertThat(unresolved.getFirst().testMethod()).isEqualTo("testIt");
    }
}
