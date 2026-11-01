package com.aichangeguard.impact.database.service;

import com.aichangeguard.analysis.model.AnnotationMetadata;
import com.aichangeguard.analysis.model.MethodMetadata;
import com.aichangeguard.analysis.model.ParameterMetadata;
import com.aichangeguard.analysis.model.ParsedFileMetadata;
import com.aichangeguard.analysis.model.SourceAnalysisReport;
import com.aichangeguard.analysis.model.TypeKind;
import com.aichangeguard.analysis.model.TypeMetadata;
import com.aichangeguard.impact.database.model.RepositoryMethod;
import com.aichangeguard.impact.database.model.SpringDataRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Extracts Spring Data Repository interfaces and query methods from parsed Java sources.
 */
@Service
public class SpringDataRepositoryExtractor {

    private static final Logger log = LoggerFactory.getLogger(SpringDataRepositoryExtractor.class);

    private static final Set<String> REPOSITORY_INTERFACES = Set.of(
            "Repository", "CrudRepository", "JpaRepository",
            "PagingAndSortingRepository", "ListCrudRepository", "ListPagingAndSortingRepository"
    );

    private static final Pattern TYPE_ARGS_PATTERN = Pattern.compile("<\\s*([A-Za-z0-9_$.]+)\\s*(?:,\\s*([A-Za-z0-9_$.]+)\\s*)?>");

    private static final Set<String> DERIVED_QUERY_PREFIXES = Set.of(
            "find", "read", "get", "query", "search", "stream", "count", "exists", "delete", "remove"
    );

    /**
     * Extracts all Spring Data repositories from the given source analysis report.
     */
    public List<SpringDataRepository> extractRepositories(SourceAnalysisReport report) {
        if (report == null || report.parsedFiles() == null) {
            return Collections.emptyList();
        }

        List<SpringDataRepository> repos = new ArrayList<>();
        for (ParsedFileMetadata file : report.parsedFiles()) {
            if (!file.isSuccessful() || file.types() == null) {
                continue;
            }
            for (TypeMetadata type : file.types()) {
                if (isRepository(type)) {
                    repos.add(buildRepository(type, file.relativePath()));
                }
            }
        }

        log.info("Extracted {} Spring Data repositories from parsed sources", repos.size());
        return repos;
    }

    private boolean isRepository(TypeMetadata type) {
        if (type.kind() != TypeKind.INTERFACE) {
            return false;
        }

        // Check if explicitly annotated with @Repository
        boolean hasRepoAnnotation = safeList(type.annotations()).stream()
                .anyMatch(a -> "Repository".equals(a.name()));
        if (hasRepoAnnotation) {
            return true;
        }

        // Check implemented/extended interfaces
        for (String iface : safeList(type.extendedTypes())) {
            for (String repoIface : REPOSITORY_INTERFACES) {
                if (iface.startsWith(repoIface) || iface.contains("." + repoIface)) {
                    return true;
                }
            }
        }

        for (String iface : safeList(type.implementedInterfaces())) {
            for (String repoIface : REPOSITORY_INTERFACES) {
                if (iface.startsWith(repoIface) || iface.contains("." + repoIface)) {
                    return true;
                }
            }
        }

        return false;
    }

    private SpringDataRepository buildRepository(TypeMetadata type, String sourceFile) {
        String domainEntity = null;
        String idType = null;

        // Try extracting domain and ID from interface generics
        List<String> allInterfaces = new ArrayList<>();
        allInterfaces.addAll(safeList(type.extendedTypes()));
        allInterfaces.addAll(safeList(type.implementedInterfaces()));

        for (String iface : allInterfaces) {
            Matcher m = TYPE_ARGS_PATTERN.matcher(iface);
            if (m.find()) {
                domainEntity = simpleTypeName(m.group(1));
                if (m.group(2) != null) {
                    idType = simpleTypeName(m.group(2));
                }
                break;
            }
        }

        List<RepositoryMethod> methods = new ArrayList<>();
        for (MethodMetadata method : safeList(type.methods())) {
            methods.add(buildMethod(method));
        }

        int sourceLine = type.range() != null && type.range().begin() != null ? type.range().begin().line() : 0;

        return new SpringDataRepository(
                type.simpleName(),
                type.qualifiedName(),
                domainEntity,
                idType,
                methods,
                sourceFile,
                sourceLine
        );
    }

    private RepositoryMethod buildMethod(MethodMetadata method) {
        String methodName = method.name();
        String returnType = method.returnTypeName();

        List<String> paramTypes = safeList(method.parameters()).stream()
                .map(ParameterMetadata::typeName)
                .toList();

        String queryType = "OTHER";
        String queryString = null;
        boolean isNative = false;
        boolean isModifying = false;

        for (AnnotationMetadata a : safeList(method.annotations())) {
            if ("Modifying".equals(a.name())) {
                isModifying = true;
            }
            if ("Query".equals(a.name())) {
                if (a.attributes() != null) {
                    String val = cleanValue(a.attributes().get("value"));
                    if (!val.isEmpty()) {
                        queryString = val;
                    }
                    String nativeAttr = a.attributes().get("nativeQuery");
                    if ("true".equalsIgnoreCase(nativeAttr)) {
                        isNative = true;
                    }
                }
                queryType = isNative ? "NATIVE_QUERY" : "JPQL_QUERY";
            }
        }

        if (queryString == null) {
            for (String prefix : DERIVED_QUERY_PREFIXES) {
                if (methodName.startsWith(prefix)) {
                    queryType = "DERIVED";
                    break;
                }
            }
        }

        int sourceLine = method.range() != null && method.range().begin() != null ? method.range().begin().line() : 0;

        return new RepositoryMethod(
                methodName,
                returnType,
                paramTypes,
                queryType,
                queryString,
                isNative,
                isModifying,
                sourceLine
        );
    }

    private String simpleTypeName(String qualifiedOrType) {
        if (qualifiedOrType == null) return null;
        int lastDot = qualifiedOrType.lastIndexOf('.');
        return lastDot >= 0 ? qualifiedOrType.substring(lastDot + 1) : qualifiedOrType;
    }

    private String cleanValue(String val) {
        if (val == null) return "";
        return val.trim()
                .replaceAll("^\"+|\"+$", "")
                .replaceAll("^'+|'+$", "")
                .trim();
    }

    private <T> List<T> safeList(List<T> list) {
        return list != null ? list : Collections.emptyList();
    }
}

