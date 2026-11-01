package com.aichangeguard.testing.service;

import com.aichangeguard.analysis.model.AnnotationMetadata;
import com.aichangeguard.analysis.model.FieldMetadata;
import com.aichangeguard.analysis.model.MethodInvocationMetadata;
import com.aichangeguard.analysis.model.MethodMetadata;
import com.aichangeguard.analysis.model.ParsedFileMetadata;
import com.aichangeguard.analysis.model.SourceAnalysisReport;
import com.aichangeguard.analysis.model.TypeKind;
import com.aichangeguard.analysis.model.TypeMetadata;
import com.aichangeguard.testing.model.TestCase;
import com.aichangeguard.testing.model.TestClass;
import com.aichangeguard.testing.model.UnresolvedTestMapping;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Discovers JUnit 5 test classes, test methods, and their production code references.
 */
@Service
public class TestDiscoveryService {

    private static final Logger log = LoggerFactory.getLogger(TestDiscoveryService.class);

    private static final Set<String> JUNIT5_TEST_ANNOTATIONS = Set.of(
            "Test", "ParameterizedTest", "RepeatedTest", "TestFactory", "TestTemplate"
    );

    private static final Set<String> SPRING_TEST_ANNOTATIONS = Set.of(
            "SpringBootTest", "WebMvcTest", "DataJpaTest", "RestClientTest", "JsonTest"
    );

    /**
     * Discovers all test classes and methods from a source analysis report.
     */
    public List<TestClass> discoverTestClasses(SourceAnalysisReport report) {
        if (report == null || report.parsedFiles() == null) {
            return Collections.emptyList();
        }

        List<TestClass> testClasses = new ArrayList<>();
        for (ParsedFileMetadata file : report.parsedFiles()) {
            if (!file.isSuccessful() || file.types() == null) {
                continue;
            }
            for (TypeMetadata type : file.types()) {
                inspectTypeForTests(type, file.relativePath(), file.imports(), testClasses);
            }
        }

        log.info("Discovered {} test classes across parsed files", testClasses.size());
        return testClasses;
    }

    private void inspectTypeForTests(TypeMetadata type, String sourceFile, List<String> fileImports, List<TestClass> result) {
        if (type.kind() != TypeKind.CLASS) {
            return;
        }

        List<TestCase> testMethods = new ArrayList<>();
        Set<String> classLevelReferencedTypes = new LinkedHashSet<>();

        // Collect class-level annotations
        List<String> classAnnotationNames = safeList(type.annotations()).stream()
                .map(AnnotationMetadata::name)
                .toList();

        // Collect referenced types from fields (e.g. @Mock, @InjectMocks, @Autowired)
        for (FieldMetadata field : safeList(type.fields())) {
            if (field.typeName() != null && !isStandardJdkType(field.typeName())) {
                classLevelReferencedTypes.add(stripGenerics(field.typeName()));
            }
        }

        // Add imports that appear to be production classes (skip java.*, org.junit.*, etc.)
        for (String imp : safeList(fileImports)) {
            if (isProductionImport(imp)) {
                classLevelReferencedTypes.add(extractSimpleName(imp));
            }
        }

        // Inspect methods for JUnit 5 annotations
        for (MethodMetadata method : safeList(type.methods())) {
            List<String> methodAnnotationNames = safeList(method.annotations()).stream()
                    .map(AnnotationMetadata::name)
                    .toList();

            boolean isTest = methodAnnotationNames.stream().anyMatch(JUNIT5_TEST_ANNOTATIONS::contains);
            if (isTest) {
                boolean isParameterized = methodAnnotationNames.contains("ParameterizedTest");
                Set<String> referencedSymbols = new LinkedHashSet<>();
                Set<String> referencedTypes = new LinkedHashSet<>(classLevelReferencedTypes);

                // Extract method invocations in this test
                for (MethodInvocationMetadata inv : safeList(method.invocations())) {
                    referencedSymbols.add(inv.methodName());
                    if (inv.scope() != null && !inv.scope().isBlank() && !isStandardJdkType(inv.scope())) {
                        referencedTypes.add(inv.scope());
                        referencedSymbols.add(inv.scope() + "." + inv.methodName());
                    }
                }

                int sourceLine = method.range() != null && method.range().begin() != null
                        ? method.range().begin().line() : 0;

                testMethods.add(new TestCase(
                        type.qualifiedName(),
                        type.simpleName(),
                        method.name(),
                        method.symbolId(),
                        sourceFile,
                        sourceLine,
                        methodAnnotationNames,
                        isParameterized,
                        referencedSymbols,
                        referencedTypes
                ));
            }
        }

        if (!testMethods.isEmpty() || classAnnotationNames.stream().anyMatch(SPRING_TEST_ANNOTATIONS::contains)) {
            int sourceLine = type.range() != null && type.range().begin() != null
                    ? type.range().begin().line() : 0;

            result.add(new TestClass(
                    type.qualifiedName(),
                    type.simpleName(),
                    sourceFile,
                    sourceLine,
                    classAnnotationNames,
                    testMethods,
                    classLevelReferencedTypes
            ));
        }

        // Recursively inspect @Nested test classes
        for (TypeMetadata nested : safeList(type.nestedTypes())) {
            inspectTypeForTests(nested, sourceFile, fileImports, result);
        }
    }

    /**
     * Extracts unresolved calls from test methods.
     */
    public List<UnresolvedTestMapping> findUnresolvedMappings(List<TestClass> testClasses) {
        List<UnresolvedTestMapping> unresolved = new ArrayList<>();
        for (TestClass tc : testClasses) {
            for (TestCase tm : safeList(tc.testMethods())) {
                // If a test method references symbols that have no qualified class target
                for (String sym : safeList(tm.referencedProductionSymbols())) {
                    if (!sym.contains(".") && !sym.contains("#")) {
                        unresolved.add(new UnresolvedTestMapping(
                                tc.qualifiedName(),
                                tm.testMethodName(),
                                sym,
                                "Method invocation scope could not be statically resolved to a production symbol",
                                tm.sourceFile(),
                                tm.sourceLine()
                        ));
                    }
                }
            }
        }
        return unresolved;
    }

    private boolean isProductionImport(String imp) {
        if (imp == null) return false;
        return !imp.startsWith("java.")
                && !imp.startsWith("javax.")
                && !imp.startsWith("jakarta.")
                && !imp.startsWith("org.junit.")
                && !imp.startsWith("org.mockito.")
                && !imp.startsWith("org.assertj.")
                && !imp.startsWith("org.springframework.test.");
    }

    private boolean isStandardJdkType(String type) {
        if (type == null) return true;
        return type.equals("String") || type.equals("int") || type.equals("long")
                || type.equals("boolean") || type.equals("void") || type.equals("Object")
                || type.startsWith("List") || type.startsWith("Map") || type.startsWith("Set")
                || type.startsWith("Optional");
    }

    private String stripGenerics(String type) {
        int idx = type.indexOf('<');
        return idx > 0 ? type.substring(0, idx).trim() : type.trim();
    }

    private String extractSimpleName(String qualified) {
        int idx = qualified.lastIndexOf('.');
        return idx >= 0 ? qualified.substring(idx + 1) : qualified;
    }

    private <T> List<T> safeList(List<T> list) {
        return list != null ? list : Collections.emptyList();
    }

    private <T> Set<T> safeList(Set<T> set) {
        return set != null ? set : Collections.emptySet();
    }
}

