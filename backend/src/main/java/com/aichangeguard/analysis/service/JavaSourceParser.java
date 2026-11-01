package com.aichangeguard.analysis.service;

import com.aichangeguard.analysis.model.AnnotationMetadata;
import com.aichangeguard.analysis.model.FieldMetadata;
import com.aichangeguard.analysis.model.MethodInvocationMetadata;
import com.aichangeguard.analysis.model.MethodMetadata;
import com.aichangeguard.analysis.model.ParameterMetadata;
import com.aichangeguard.analysis.model.ParsedFileMetadata;
import com.aichangeguard.analysis.model.ParsingDiagnostic;
import com.aichangeguard.analysis.model.SourceAnalysisReport;
import com.aichangeguard.analysis.model.SourcePosition;
import com.aichangeguard.analysis.model.SourceRange;
import com.aichangeguard.analysis.model.TypeKind;
import com.aichangeguard.analysis.model.TypeMetadata;
import com.github.javaparser.JavaParser;
import com.github.javaparser.ParseProblemException;
import com.github.javaparser.ParseResult;
import com.github.javaparser.ParserConfiguration;
import com.github.javaparser.Problem;
import com.github.javaparser.Range;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.Modifier;
import com.github.javaparser.ast.Node;
import com.github.javaparser.ast.body.AnnotationDeclaration;
import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration;
import com.github.javaparser.ast.body.ConstructorDeclaration;
import com.github.javaparser.ast.body.EnumDeclaration;
import com.github.javaparser.ast.body.FieldDeclaration;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.body.Parameter;
import com.github.javaparser.ast.body.RecordDeclaration;
import com.github.javaparser.ast.body.TypeDeclaration;
import com.github.javaparser.ast.body.VariableDeclarator;
import com.github.javaparser.ast.expr.AnnotationExpr;
import com.github.javaparser.ast.expr.MemberValuePair;
import com.github.javaparser.ast.expr.MethodCallExpr;
import com.github.javaparser.ast.expr.NormalAnnotationExpr;
import com.github.javaparser.ast.expr.ObjectCreationExpr;
import com.github.javaparser.ast.expr.SingleMemberAnnotationExpr;
import com.github.javaparser.ast.stmt.ExplicitConstructorInvocationStmt;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Deterministic Java source parser using JavaParser.
 * Converts Java source code into structured AST metadata while capturing parsing diagnostics.
 */
@Service
public class JavaSourceParser {

    private static final Logger log = LoggerFactory.getLogger(JavaSourceParser.class);

    private final JavaParser javaParser;

    public JavaSourceParser() {
        ParserConfiguration configuration = new ParserConfiguration();
        configuration.setLanguageLevel(ParserConfiguration.LanguageLevel.JAVA_21);
        configuration.setAttributeComments(false);
        this.javaParser = new JavaParser(configuration);
    }

    /**
     * Parses a Java source string into structured metadata.
     *
     * @param relativePath Relative file path for tracking provenance
     * @param sourceContent Source code string
     * @return Parsed file metadata
     */
    public ParsedFileMetadata parseSource(String relativePath, String sourceContent) {
        List<ParsingDiagnostic> diagnostics = new ArrayList<>();
        ParseResult<CompilationUnit> parseResult;

        try {
            parseResult = javaParser.parse(sourceContent);
        } catch (ParseProblemException e) {
            log.warn("Parsing failure in '{}': {}", relativePath, e.getMessage());
            for (Problem problem : e.getProblems()) {
                diagnostics.add(toDiagnostic(relativePath, problem));
            }
            return new ParsedFileMetadata(relativePath, "", Collections.emptyList(),
                    Collections.emptyList(), diagnostics, false, SourceRange.UNKNOWN);
        } catch (Exception e) {
            log.error("Unexpected parser error in '{}': {}", relativePath, e.getMessage());
            diagnostics.add(new ParsingDiagnostic(relativePath, "ERROR", e.getMessage(), SourcePosition.UNKNOWN));
            return new ParsedFileMetadata(relativePath, "", Collections.emptyList(),
                    Collections.emptyList(), diagnostics, false, SourceRange.UNKNOWN);
        }

        for (Problem problem : parseResult.getProblems()) {
            diagnostics.add(toDiagnostic(relativePath, problem));
        }

        Optional<CompilationUnit> cuOpt = parseResult.getResult();
        if (cuOpt.isEmpty()) {
            return new ParsedFileMetadata(relativePath, "", Collections.emptyList(),
                    Collections.emptyList(), diagnostics, false, SourceRange.UNKNOWN);
        }

        CompilationUnit cu = cuOpt.get();
        boolean isClean = parseResult.isSuccessful();

        String packageName = cu.getPackageDeclaration()
                .map(pd -> pd.getNameAsString())
                .orElse("");

        List<String> imports = cu.getImports().stream()
                .map(Node::toString)
                .map(String::trim)
                .toList();

        List<TypeMetadata> types = new ArrayList<>();
        for (TypeDeclaration<?> typeDeclaration : cu.getTypes()) {
            types.add(extractType(typeDeclaration, packageName, null));
        }

        SourceRange fileRange = toSourceRange(cu.getRange());

        return new ParsedFileMetadata(
                relativePath,
                packageName,
                imports,
                types,
                diagnostics,
                isClean,
                fileRange
        );
    }

    /**
     * Parses an on-disk Java file.
     */
    public ParsedFileMetadata parseFile(Path filePath, Path repoRoot) {
        String relativePath = repoRoot != null
                ? repoRoot.relativize(filePath).toString().replace('\\', '/')
                : filePath.getFileName().toString();

        try {
            String content = Files.readString(filePath);
            return parseSource(relativePath, content);
        } catch (IOException e) {
            log.warn("Failed to read file '{}': {}", filePath, e.getMessage());
            List<ParsingDiagnostic> diagnostics = List.of(
                    new ParsingDiagnostic(relativePath, "ERROR", "Failed to read file: " + e.getMessage(), SourcePosition.UNKNOWN)
            );
            return new ParsedFileMetadata(relativePath, "", Collections.emptyList(),
                    Collections.emptyList(), diagnostics, false, SourceRange.UNKNOWN);
        }
    }

    /**
     * Parses a batch of files across a repository, aggregating coverage and limitations.
     */
    public SourceAnalysisReport parseRepository(Path repoRoot, List<String> relativePaths) {
        List<ParsedFileMetadata> parsedFiles = new ArrayList<>();
        List<ParsingDiagnostic> allDiagnostics = new ArrayList<>();
        int successful = 0;
        int failed = 0;

        for (String relPath : relativePaths) {
            Path fullPath = repoRoot.resolve(relPath);
            ParsedFileMetadata fileMeta = parseFile(fullPath, repoRoot);
            parsedFiles.add(fileMeta);
            allDiagnostics.addAll(fileMeta.diagnostics());

            if (fileMeta.isSuccessful()) {
                successful++;
            } else {
                failed++;
            }
        }

        int total = relativePaths.size();
        double coverage = total > 0 ? ((double) successful / total) * 100.0 : 0.0;

        List<String> limitations = List.of(
                "Source code AST parsing extracts syntactic declarations and method calls.",
                "Target resolution for method invocations remains unconfirmed without symbol solver or compiler classpath.",
                "Reflection, dynamic proxies, and framework runtime injection are outside static AST scope."
        );

        return new SourceAnalysisReport(
                repoRoot.toString(),
                total,
                successful,
                failed,
                Math.round(coverage * 100.0) / 100.0,
                parsedFiles,
                allDiagnostics,
                Instant.now(),
                limitations
        );
    }

    private TypeMetadata extractType(TypeDeclaration<?> td, String packageName, String outerPrefix) {
        String simpleName = td.getNameAsString();
        String qualifiedName;
        if (outerPrefix != null && !outerPrefix.isBlank()) {
            qualifiedName = outerPrefix + "." + simpleName;
        } else if (packageName != null && !packageName.isBlank()) {
            qualifiedName = packageName + "." + simpleName;
        } else {
            qualifiedName = simpleName;
        }

        String symbolId = qualifiedName;
        TypeKind kind = determineTypeKind(td);
        Set<String> modifiers = extractModifiers(td.getModifiers());
        List<AnnotationMetadata> annotations = extractAnnotations(td.getAnnotations());

        List<String> extendedTypes = new ArrayList<>();
        List<String> implementedInterfaces = new ArrayList<>();

        if (td instanceof ClassOrInterfaceDeclaration cid) {
            extendedTypes = cid.getExtendedTypes().stream().map(Node::toString).map(String::trim).toList();
            implementedInterfaces = cid.getImplementedTypes().stream().map(Node::toString).map(String::trim).toList();
        } else if (td instanceof RecordDeclaration rd) {
            implementedInterfaces = rd.getImplementedTypes().stream().map(Node::toString).map(String::trim).toList();
        } else if (td instanceof EnumDeclaration ed) {
            implementedInterfaces = ed.getImplementedTypes().stream().map(Node::toString).map(String::trim).toList();
        }

        // Fields
        List<FieldMetadata> fields = new ArrayList<>();
        for (FieldDeclaration fd : td.getFields()) {
            Set<String> fieldMods = extractModifiers(fd.getModifiers());
            List<AnnotationMetadata> fieldAnnos = extractAnnotations(fd.getAnnotations());
            String typeName = fd.getElementType().asString();
            SourceRange range = toSourceRange(fd.getRange());

            for (VariableDeclarator vd : fd.getVariables()) {
                String fieldName = vd.getNameAsString();
                String fieldSymbolId = qualifiedName + "#" + fieldName;
                fields.add(new FieldMetadata(fieldSymbolId, fieldName, typeName, fieldMods, fieldAnnos, range));
            }
        }

        // Constructors
        List<MethodMetadata> constructors = new ArrayList<>();
        for (ConstructorDeclaration cd : td.getConstructors()) {
            constructors.add(extractConstructor(cd, qualifiedName));
        }

        // Methods
        List<MethodMetadata> methods = new ArrayList<>();
        for (MethodDeclaration md : td.getMethods()) {
            methods.add(extractMethod(md, qualifiedName, kind == TypeKind.INTERFACE));
        }

        // Nested Types
        List<TypeMetadata> nestedTypes = new ArrayList<>();
        for (Node member : td.getMembers()) {
            if (member instanceof TypeDeclaration<?> nestedTd) {
                nestedTypes.add(extractType(nestedTd, packageName, qualifiedName));
            }
        }

        SourceRange range = toSourceRange(td.getRange());

        return new TypeMetadata(
                symbolId,
                qualifiedName,
                simpleName,
                kind,
                modifiers,
                annotations,
                extendedTypes,
                implementedInterfaces,
                fields,
                constructors,
                methods,
                nestedTypes,
                range
        );
    }

    private MethodMetadata extractConstructor(ConstructorDeclaration cd, String qualifiedTypeName) {
        String name = "<init>";
        List<ParameterMetadata> parameters = extractParameters(cd.getParameters());
        List<String> paramTypes = parameters.stream().map(ParameterMetadata::typeName).toList();
        String symbolId = "%s#<init>(%s)".formatted(qualifiedTypeName, String.join(",", paramTypes));

        Set<String> modifiers = extractModifiers(cd.getModifiers());
        List<AnnotationMetadata> annotations = extractAnnotations(cd.getAnnotations());
        List<String> thrownExceptions = cd.getThrownExceptions().stream().map(Node::toString).toList();
        SourceRange range = toSourceRange(cd.getRange());

        List<MethodInvocationMetadata> invocations = new ArrayList<>();
        if (cd.getBody() != null) {
            invocations.addAll(extractInvocations(cd.getBody()));
        }

        return new MethodMetadata(
                symbolId,
                name,
                "void",
                parameters,
                modifiers,
                annotations,
                thrownExceptions,
                true,
                false,
                range,
                invocations
        );
    }

    private MethodMetadata extractMethod(MethodDeclaration md, String qualifiedTypeName, boolean isInterfaceEnclosed) {
        String name = md.getNameAsString();
        String returnType = md.getTypeAsString();
        List<ParameterMetadata> parameters = extractParameters(md.getParameters());
        List<String> paramTypes = parameters.stream().map(ParameterMetadata::typeName).toList();
        String symbolId = "%s#%s(%s)".formatted(qualifiedTypeName, name, String.join(",", paramTypes));

        Set<String> modifiers = extractModifiers(md.getModifiers());
        List<AnnotationMetadata> annotations = extractAnnotations(md.getAnnotations());
        List<String> thrownExceptions = md.getThrownExceptions().stream().map(Node::toString).toList();

        boolean isAbstract = md.isAbstract() || (isInterfaceEnclosed && !md.isDefault() && !md.isStatic());
        SourceRange range = toSourceRange(md.getRange());

        List<MethodInvocationMetadata> invocations = new ArrayList<>();
        md.getBody().ifPresent(body -> invocations.addAll(extractInvocations(body)));

        return new MethodMetadata(
                symbolId,
                name,
                returnType,
                parameters,
                modifiers,
                annotations,
                thrownExceptions,
                false,
                isAbstract,
                range,
                invocations
        );
    }

    private List<MethodInvocationMetadata> extractInvocations(Node node) {
        List<MethodInvocationMetadata> invocations = new ArrayList<>();

        // 1. MethodCallExpr
        for (MethodCallExpr call : node.findAll(MethodCallExpr.class)) {
            String scope = call.getScope().map(Node::toString).orElse(null);
            String methodName = call.getNameAsString();
            int argCount = call.getArguments().size();
            SourceRange range = toSourceRange(call.getRange());
            invocations.add(MethodInvocationMetadata.syntactic(scope, methodName, argCount, range));
        }

        // 2. ObjectCreationExpr (new Foo())
        for (ObjectCreationExpr creation : node.findAll(ObjectCreationExpr.class)) {
            String typeName = creation.getTypeAsString();
            int argCount = creation.getArguments().size();
            SourceRange range = toSourceRange(creation.getRange());
            invocations.add(MethodInvocationMetadata.syntactic(typeName, "<init>", argCount, range));
        }

        // 3. ExplicitConstructorInvocationStmt (super() / this())
        for (ExplicitConstructorInvocationStmt stmt : node.findAll(ExplicitConstructorInvocationStmt.class)) {
            String target = stmt.isThis() ? "this" : "super";
            int argCount = stmt.getArguments().size();
            SourceRange range = toSourceRange(stmt.getRange());
            invocations.add(MethodInvocationMetadata.syntactic(null, target, argCount, range));
        }

        return invocations;
    }

    private List<ParameterMetadata> extractParameters(List<Parameter> parameters) {
        List<ParameterMetadata> list = new ArrayList<>();
        for (Parameter p : parameters) {
            String name = p.getNameAsString();
            String typeName = p.getTypeAsString();
            boolean isVarargs = p.isVarArgs();
            List<AnnotationMetadata> annotations = extractAnnotations(p.getAnnotations());
            list.add(new ParameterMetadata(name, typeName, annotations, isVarargs));
        }
        return list;
    }

    private List<AnnotationMetadata> extractAnnotations(List<AnnotationExpr> annotationExprs) {
        List<AnnotationMetadata> list = new ArrayList<>();
        for (AnnotationExpr expr : annotationExprs) {
            String name = expr.getNameAsString();
            Map<String, String> attributes = new HashMap<>();

            if (expr instanceof SingleMemberAnnotationExpr single) {
                attributes.put("value", single.getMemberValue().toString());
            } else if (expr instanceof NormalAnnotationExpr normal) {
                for (MemberValuePair pair : normal.getPairs()) {
                    attributes.put(pair.getNameAsString(), pair.getValue().toString());
                }
            }

            SourceRange range = toSourceRange(expr.getRange());
            list.add(new AnnotationMetadata(name, attributes, range));
        }
        return list;
    }

    private Set<String> extractModifiers(List<Modifier> modifiers) {
        return modifiers.stream()
                .map(m -> m.getKeyword().asString())
                .collect(Collectors.toSet());
    }

    private TypeKind determineTypeKind(TypeDeclaration<?> td) {
        if (td instanceof ClassOrInterfaceDeclaration cid) {
            return cid.isInterface() ? TypeKind.INTERFACE : TypeKind.CLASS;
        } else if (td instanceof RecordDeclaration) {
            return TypeKind.RECORD;
        } else if (td instanceof EnumDeclaration) {
            return TypeKind.ENUM;
        } else if (td instanceof AnnotationDeclaration) {
            return TypeKind.ANNOTATION;
        }
        return TypeKind.CLASS;
    }

    private ParsingDiagnostic toDiagnostic(String filePath, Problem problem) {
        String msg = problem.getMessage();
        SourcePosition pos = problem.getLocation()
                .flatMap(loc -> loc.getBegin().getRange())
                .map(r -> new SourcePosition(r.begin.line, r.begin.column))
                .orElse(SourcePosition.UNKNOWN);
        return new ParsingDiagnostic(filePath, "ERROR", msg, pos);
    }

    private SourceRange toSourceRange(Optional<Range> rangeOpt) {
        return rangeOpt.map(r -> SourceRange.of(
                r.begin.line, r.begin.column,
                r.end.line, r.end.column
        )).orElse(SourceRange.UNKNOWN);
    }
}
