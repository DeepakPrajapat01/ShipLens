package com.aichangeguard.impact.database.service;

import com.aichangeguard.impact.database.model.SqlStatementInfo;
import com.aichangeguard.impact.database.model.SqlStatementType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Deterministic static parser for SQL statements in Flyway migration files.
 *
 * <p>Requirements:
 * <ul>
 *   <li>NEVER executes SQL statements or connects to a database.</li>
 *   <li>Identifies destructive DDL and DML operations (DROP TABLE, DROP COLUMN, TRUNCATE, unconditional DELETE).</li>
 *   <li>Identifies schema modifications (ADD/DROP COLUMN, ALTER TYPE, SET/DROP NOT NULL, ADD/DROP CONSTRAINT, CREATE/DROP INDEX).</li>
 *   <li>Explicitly flags unsupported SQL statements (stored procedures, DO $$ blocks, triggers, vendor extensions).</li>
 *   <li>Preserves line numbers for traceability.</li>
 * </ul>
 */
@Service
public class SqlStatementParser {

    private static final Logger log = LoggerFactory.getLogger(SqlStatementParser.class);

    // DDL Regex Patterns (Case insensitive)
    private static final Pattern CREATE_TABLE_PATTERN = Pattern.compile(
            "^\\s*CREATE\\s+TABLE\\s+(?:IF\\s+NOT\\s+EXISTS\\s+)?([`\"'\\[]?([A-Za-z0-9_$.]+)[`\"'\\]]?)",
            Pattern.CASE_INSENSITIVE
    );

    private static final Pattern DROP_TABLE_PATTERN = Pattern.compile(
            "^\\s*DROP\\s+TABLE\\s+(?:IF\\s+EXISTS\\s+)?([`\"'\\[]?([A-Za-z0-9_$.]+)[`\"'\\]]?)",
            Pattern.CASE_INSENSITIVE
    );

    private static final Pattern TRUNCATE_PATTERN = Pattern.compile(
            "^\\s*TRUNCATE\\s+(?:TABLE\\s+)?([`\"'\\[]?([A-Za-z0-9_$.]+)[`\"'\\]]?)",
            Pattern.CASE_INSENSITIVE
    );

    private static final Pattern ALTER_TABLE_PATTERN = Pattern.compile(
            "^\\s*ALTER\\s+TABLE\\s+(?:ONLY\\s+)?([`\"'\\[]?([A-Za-z0-9_$.]+)[`\"'\\]]?)\\s+(.+)",
            Pattern.CASE_INSENSITIVE | Pattern.DOTALL
    );

    private static final Pattern CREATE_INDEX_PATTERN = Pattern.compile(
            "^\\s*CREATE\\s+(?:UNIQUE\\s+)?INDEX\\s+(?:CONCURRENTLY\\s+)?(?:IF\\s+NOT\\s+EXISTS\\s+)?([`\"'\\[]?([A-Za-z0-9_$.]+)[`\"'\\]]?)\\s+ON\\s+([`\"'\\[]?([A-Za-z0-9_$.]+)[`\"'\\]]?)",
            Pattern.CASE_INSENSITIVE
    );

    private static final Pattern DROP_INDEX_PATTERN = Pattern.compile(
            "^\\s*DROP\\s+INDEX\\s+(?:CONCURRENTLY\\s+)?(?:IF\\s+EXISTS\\s+)?([`\"'\\[]?([A-Za-z0-9_$.]+)[`\"'\\]]?)",
            Pattern.CASE_INSENSITIVE
    );

    private static final Pattern DELETE_PATTERN = Pattern.compile(
            "^\\s*DELETE\\s+FROM\\s+([`\"'\\[]?([A-Za-z0-9_$.]+)[`\"'\\]]?)(.*)",
            Pattern.CASE_INSENSITIVE | Pattern.DOTALL
    );

    private static final Pattern UPDATE_PATTERN = Pattern.compile(
            "^\\s*UPDATE\\s+(?:ONLY\\s+)?([`\"'\\[]?([A-Za-z0-9_$.]+)[`\"'\\]]?)\\s+SET\\s+(.*)",
            Pattern.CASE_INSENSITIVE | Pattern.DOTALL
    );

    // Unsupported constructs patterns
    private static final Pattern UNSUPPORTED_PROC_PATTERN = Pattern.compile(
            "^\\s*CREATE\\s+(?:OR\\s+REPLACE\\s+)?(?:PROCEDURE|FUNCTION|TRIGGER|VIEW|MATERIALIZED\\s+VIEW|TYPE|EXTENSION|RULE)\\b",
            Pattern.CASE_INSENSITIVE
    );

    private static final Pattern UNSUPPORTED_BLOCK_PATTERN = Pattern.compile(
            "^\\s*(?:DO\\s+\\$\\$|BEGIN\\s|DECLARE\\s)",
            Pattern.CASE_INSENSITIVE
    );

    /**
     * Parses the full content of an SQL script into structured {@link SqlStatementInfo} records.
     */
    public List<SqlStatementInfo> parseScript(String sqlContent) {
        if (sqlContent == null || sqlContent.isBlank()) {
            return List.of();
        }

        List<RawStatement> rawStatements = splitStatements(sqlContent);
        List<SqlStatementInfo> result = new ArrayList<>();

        for (RawStatement raw : rawStatements) {
            SqlStatementInfo info = parseSingleStatement(raw.text().trim(), raw.lineNumber());
            if (info != null) {
                result.add(info);
            }
        }

        return result;
    }

    /**
     * Parses a single statement string into {@link SqlStatementInfo}.
     */
    public SqlStatementInfo parseSingleStatement(String sql, int lineNumber) {
        if (sql == null || sql.isBlank()) {
            return null;
        }

        int effectiveLineNumber = findEffectiveLineNumber(sql, lineNumber);
        String cleaned = cleanComments(sql).trim();
        if (cleaned.isEmpty()) {
            return null;
        }

        // Check unsupported procedural or block constructs
        if (UNSUPPORTED_PROC_PATTERN.matcher(cleaned).find() || UNSUPPORTED_BLOCK_PATTERN.matcher(cleaned).find()) {
            return new SqlStatementInfo(
                    sql,
                    SqlStatementType.UNSUPPORTED,
                    null, null, null, null, null, null, null,
                    false, null,
                    "Unsupported procedural or PL/SQL construct",
                    effectiveLineNumber
            );
        }

        // 1. CREATE TABLE
        Matcher createTableMatcher = CREATE_TABLE_PATTERN.matcher(cleaned);
        if (createTableMatcher.find()) {
            String table = cleanIdentifier(createTableMatcher.group(2) != null ? createTableMatcher.group(2) : createTableMatcher.group(1));
            return new SqlStatementInfo(
                    sql,
                    SqlStatementType.CREATE_TABLE,
                    table, null, null, null, null, null, null,
                    false, null, null,
                    effectiveLineNumber
            );
        }

        // 2. DROP TABLE
        Matcher dropTableMatcher = DROP_TABLE_PATTERN.matcher(cleaned);
        if (dropTableMatcher.find()) {
            String table = cleanIdentifier(dropTableMatcher.group(2) != null ? dropTableMatcher.group(2) : dropTableMatcher.group(1));
            return new SqlStatementInfo(
                    sql,
                    SqlStatementType.DROP_TABLE,
                    table, null, null, null, null, null, null,
                    true, "Table dropped: " + table, null,
                    effectiveLineNumber
            );
        }

        // 3. TRUNCATE TABLE
        Matcher truncateMatcher = TRUNCATE_PATTERN.matcher(cleaned);
        if (truncateMatcher.find()) {
            String table = cleanIdentifier(truncateMatcher.group(2) != null ? truncateMatcher.group(2) : truncateMatcher.group(1));
            return new SqlStatementInfo(
                    sql,
                    SqlStatementType.TRUNCATE_TABLE,
                    table, null, null, null, null, null, null,
                    true, "Table truncated: " + table, null,
                    effectiveLineNumber
            );
        }

        // 4. CREATE INDEX
        Matcher createIndexMatcher = CREATE_INDEX_PATTERN.matcher(cleaned);
        if (createIndexMatcher.find()) {
            String indexName = cleanIdentifier(createIndexMatcher.group(2) != null ? createIndexMatcher.group(2) : createIndexMatcher.group(1));
            String table = cleanIdentifier(createIndexMatcher.group(4) != null ? createIndexMatcher.group(4) : createIndexMatcher.group(3));
            return new SqlStatementInfo(
                    sql,
                    SqlStatementType.CREATE_INDEX,
                    table, null, null, null, null, null, indexName,
                    false, null, null,
                    effectiveLineNumber
            );
        }

        // 5. DROP INDEX
        Matcher dropIndexMatcher = DROP_INDEX_PATTERN.matcher(cleaned);
        if (dropIndexMatcher.find()) {
            String indexName = cleanIdentifier(dropIndexMatcher.group(2) != null ? dropIndexMatcher.group(2) : dropIndexMatcher.group(1));
            return new SqlStatementInfo(
                    sql,
                    SqlStatementType.DROP_INDEX,
                    null, null, null, null, null, null, indexName,
                    false, null, null,
                    effectiveLineNumber
            );
        }

        // 6. ALTER TABLE
        Matcher alterTableMatcher = ALTER_TABLE_PATTERN.matcher(cleaned);
        if (alterTableMatcher.find()) {
            String table = cleanIdentifier(alterTableMatcher.group(2) != null ? alterTableMatcher.group(2) : alterTableMatcher.group(1));
            String action = alterTableMatcher.group(3).trim();
            return parseAlterTableAction(table, action, sql, effectiveLineNumber);
        }

        // 7. DELETE
        Matcher deleteMatcher = DELETE_PATTERN.matcher(cleaned);
        if (deleteMatcher.find()) {
            String table = cleanIdentifier(deleteMatcher.group(2) != null ? deleteMatcher.group(2) : deleteMatcher.group(1));
            String rest = deleteMatcher.group(3).trim();
            boolean hasWhere = Pattern.compile("\\bWHERE\\b", Pattern.CASE_INSENSITIVE).matcher(rest).find();
            boolean isDestructive = !hasWhere;
            String reason = isDestructive ? "Unconditional DELETE FROM " + table : null;
            return new SqlStatementInfo(
                    sql,
                    SqlStatementType.DELETE_DML,
                    table, null, null, null, null, null, null,
                    isDestructive, reason, null,
                    effectiveLineNumber
            );
        }

        // 8. UPDATE
        Matcher updateMatcher = UPDATE_PATTERN.matcher(cleaned);
        if (updateMatcher.find()) {
            String table = cleanIdentifier(updateMatcher.group(2) != null ? updateMatcher.group(2) : updateMatcher.group(1));
            String rest = updateMatcher.group(3).trim();
            boolean hasWhere = Pattern.compile("\\bWHERE\\b", Pattern.CASE_INSENSITIVE).matcher(rest).find();
            boolean isDestructive = !hasWhere;
            String reason = isDestructive ? "Unconditional UPDATE on " + table : null;
            return new SqlStatementInfo(
                    sql,
                    SqlStatementType.UPDATE_DML,
                    table, null, null, null, null, null, null,
                    isDestructive, reason, null,
                    effectiveLineNumber
            );
        }

        // 9. INSERT
        if (Pattern.compile("^\\s*INSERT\\s+INTO\\b", Pattern.CASE_INSENSITIVE).matcher(cleaned).find()) {
            return new SqlStatementInfo(
                    sql,
                    SqlStatementType.INSERT_DML,
                    null, null, null, null, null, null, null,
                    false, null, null,
                    effectiveLineNumber
            );
        }

        // If unrecognized statement
        return new SqlStatementInfo(
                sql,
                SqlStatementType.UNSUPPORTED,
                null, null, null, null, null, null, null,
                false, null,
                "Unrecognized or unsupported SQL statement",
                effectiveLineNumber
        );
    }

    private SqlStatementInfo parseAlterTableAction(String table, String action, String rawSql, int lineNumber) {
        String lower = action.toLowerCase();

        // ALTER TABLE ... DROP COLUMN
        if (lower.startsWith("drop column") || lower.startsWith("drop ")) {
            Pattern p = Pattern.compile("drop\\s+(?:column\\s+)?(?:if\\s+exists\\s+)?([`\"'\\[]?([A-Za-z0-9_$.]+)[`\"'\\]]?)", Pattern.CASE_INSENSITIVE);
            Matcher m = p.matcher(action);
            if (m.find()) {
                String col = cleanIdentifier(m.group(2) != null ? m.group(2) : m.group(1));
                return new SqlStatementInfo(
                        rawSql,
                        SqlStatementType.ALTER_TABLE_DROP_COLUMN,
                        table, col, null, null, null, null, null,
                        true, "Column dropped from " + table + ": " + col, null,
                        lineNumber
                );
            }
        }

        // ALTER TABLE ... ADD COLUMN
        if (lower.startsWith("add column") || lower.startsWith("add ")) {
            Pattern p = Pattern.compile("add\\s+(?:column\\s+)?(?:if\\s+not\\s+exists\\s+)?([`\"'\\[]?([A-Za-z0-9_$.]+)[`\"'\\]]?)\\s+([A-Za-z0-9_()]+)(.*)", Pattern.CASE_INSENSITIVE);
            Matcher m = p.matcher(action);
            if (m.find()) {
                String col = cleanIdentifier(m.group(2) != null ? m.group(2) : m.group(1));
                String type = m.group(3).trim();
                String rest = m.group(4) != null ? m.group(4) : "";

                boolean isNotNull = Pattern.compile("\\bNOT\\s+NULL\\b", Pattern.CASE_INSENSITIVE).matcher(rest).find();
                Matcher defMatcher = Pattern.compile("\\bDEFAULT\\s+([^,;]+)", Pattern.CASE_INSENSITIVE).matcher(rest);
                String defaultValue = defMatcher.find() ? defMatcher.group(1).trim() : null;

                // Adding NOT NULL without DEFAULT on an existing table will fail if rows exist!
                boolean potentiallyBreaking = isNotNull && defaultValue == null;

                return new SqlStatementInfo(
                        rawSql,
                        SqlStatementType.ALTER_TABLE_ADD_COLUMN,
                        table, col, type, !isNotNull, defaultValue, null, null,
                        potentiallyBreaking,
                        potentiallyBreaking ? "Added NOT NULL column '" + col + "' to table '" + table + "' without DEFAULT" : null,
                        null,
                        lineNumber
                );
            }

            // ADD CONSTRAINT
            Pattern cPattern = Pattern.compile("add\\s+constraint\\s+([`\"'\\[]?([A-Za-z0-9_$.]+)[`\"'\\]]?)", Pattern.CASE_INSENSITIVE);
            Matcher cm = cPattern.matcher(action);
            if (cm.find()) {
                String cname = cleanIdentifier(cm.group(2) != null ? cm.group(2) : cm.group(1));
                return new SqlStatementInfo(
                        rawSql,
                        SqlStatementType.ALTER_TABLE_ADD_CONSTRAINT,
                        table, null, null, null, null, cname, null,
                        false, null, null,
                        lineNumber
                );
            }
        }

        // ALTER TABLE ... ALTER COLUMN ... TYPE
        if (lower.contains("alter column") || lower.contains("alter ")) {
            Pattern typePattern = Pattern.compile("alter\\s+(?:column\\s+)?([`\"'\\[]?([A-Za-z0-9_$.]+)[`\"'\\]]?)\\s+(?:type|set\\s+data\\s+type)\\s+([A-Za-z0-9_(), ]+)", Pattern.CASE_INSENSITIVE);
            Matcher tm = typePattern.matcher(action);
            if (tm.find()) {
                String col = cleanIdentifier(tm.group(2) != null ? tm.group(2) : tm.group(1));
                String newType = tm.group(3).trim();
                return new SqlStatementInfo(
                        rawSql,
                        SqlStatementType.ALTER_TABLE_ALTER_COLUMN_TYPE,
                        table, col, newType, null, null, null, null,
                        false, null, null,
                        lineNumber
                );
            }

            // SET NOT NULL
            Pattern setNotNullPattern = Pattern.compile("alter\\s+(?:column\\s+)?([`\"'\\[]?([A-Za-z0-9_$.]+)[`\"'\\]]?)\\s+set\\s+not\\s+null", Pattern.CASE_INSENSITIVE);
            Matcher snm = setNotNullPattern.matcher(action);
            if (snm.find()) {
                String col = cleanIdentifier(snm.group(2) != null ? snm.group(2) : snm.group(1));
                return new SqlStatementInfo(
                        rawSql,
                        SqlStatementType.ALTER_TABLE_SET_NOT_NULL,
                        table, col, null, false, null, null, null,
                        true, "Column '" + col + "' in table '" + table + "' set to NOT NULL", null,
                        lineNumber
                );
            }

            // DROP NOT NULL
            Pattern dropNotNullPattern = Pattern.compile("alter\\s+(?:column\\s+)?([`\"'\\[]?([A-Za-z0-9_$.]+)[`\"'\\]]?)\\s+drop\\s+not\\s+null", Pattern.CASE_INSENSITIVE);
            Matcher dnm = dropNotNullPattern.matcher(action);
            if (dnm.find()) {
                String col = cleanIdentifier(dnm.group(2) != null ? dnm.group(2) : dnm.group(1));
                return new SqlStatementInfo(
                        rawSql,
                        SqlStatementType.ALTER_TABLE_DROP_NOT_NULL,
                        table, col, null, true, null, null, null,
                        false, null, null,
                        lineNumber
                );
            }
        }

        // DROP CONSTRAINT
        if (lower.contains("drop constraint")) {
            Pattern dcPattern = Pattern.compile("drop\\s+constraint\\s+(?:if\\s+exists\\s+)?([`\"'\\[]?([A-Za-z0-9_$.]+)[`\"'\\]]?)", Pattern.CASE_INSENSITIVE);
            Matcher dcm = dcPattern.matcher(action);
            if (dcm.find()) {
                String cname = cleanIdentifier(dcm.group(2) != null ? dcm.group(2) : dcm.group(1));
                return new SqlStatementInfo(
                        rawSql,
                        SqlStatementType.ALTER_TABLE_DROP_CONSTRAINT,
                        table, null, null, null, null, cname, null,
                        false, null, null,
                        lineNumber
                );
            }
        }

        // RENAME COLUMN
        if (lower.contains("rename column")) {
            Pattern rcPattern = Pattern.compile("rename\\s+column\\s+([`\"'\\[]?([A-Za-z0-9_$.]+)[`\"'\\]]?)\\s+to\\s+([`\"'\\[]?([A-Za-z0-9_$.]+)[`\"'\\]]?)", Pattern.CASE_INSENSITIVE);
            Matcher rcm = rcPattern.matcher(action);
            if (rcm.find()) {
                String oldCol = cleanIdentifier(rcm.group(2) != null ? rcm.group(2) : rcm.group(1));
                String newCol = cleanIdentifier(rcm.group(4) != null ? rcm.group(4) : rcm.group(3));
                return new SqlStatementInfo(
                        rawSql,
                        SqlStatementType.ALTER_TABLE_RENAME_COLUMN,
                        table, oldCol, null, null, newCol, null, null,
                        true, "Column renamed from " + oldCol + " to " + newCol + " in " + table, null,
                        lineNumber
                );
            }
        }

        // RENAME TO (rename table)
        if (lower.contains("rename to")) {
            Pattern rtPattern = Pattern.compile("rename\\s+to\\s+([`\"'\\[]?([A-Za-z0-9_$.]+)[`\"'\\]]?)", Pattern.CASE_INSENSITIVE);
            Matcher rtm = rtPattern.matcher(action);
            if (rtm.find()) {
                String newTable = cleanIdentifier(rtm.group(2) != null ? rtm.group(2) : rtm.group(1));
                return new SqlStatementInfo(
                        rawSql,
                        SqlStatementType.ALTER_TABLE_RENAME_TABLE,
                        table, null, null, null, newTable, null, null,
                        true, "Table renamed from " + table + " to " + newTable, null,
                        lineNumber
                );
            }
        }

        // Other ALTER TABLE statement
        return new SqlStatementInfo(
                rawSql,
                SqlStatementType.OTHER_DDL,
                table, null, null, null, null, null, null,
                false, null, null,
                lineNumber
        );
    }

    private int findEffectiveLineNumber(String sql, int baseLine) {
        String[] lines = sql.split("\r?\n", -1);
        boolean inBlock = false;
        int lineOffset = 0;

        for (String line : lines) {
            String trimmed = line.trim();
            if (inBlock) {
                int endIdx = trimmed.indexOf("*/");
                if (endIdx >= 0) {
                    inBlock = false;
                    trimmed = trimmed.substring(endIdx + 2).trim();
                } else {
                    lineOffset++;
                    continue;
                }
            }

            // Remove block comment if on same line
            while (trimmed.contains("/*")) {
                int startIdx = trimmed.indexOf("/*");
                int endIdx = trimmed.indexOf("*/", startIdx + 2);
                if (endIdx >= 0) {
                    trimmed = (trimmed.substring(0, startIdx) + trimmed.substring(endIdx + 2)).trim();
                } else {
                    inBlock = true;
                    trimmed = trimmed.substring(0, startIdx).trim();
                    break;
                }
            }

            // Remove line comment
            if (trimmed.startsWith("--")) {
                trimmed = "";
            }

            if (trimmed.isEmpty()) {
                lineOffset++;
            } else {
                break;
            }
        }

        return baseLine + lineOffset;
    }

    private String cleanComments(String sql) {
        // Strip single line comments -- ...
        String noLineComments = sql.replaceAll("(?m)--.*$", "");
        // Strip block comments /* ... */ matching across lines
        return noLineComments.replaceAll("(?s)/\\*.*?\\*/", "");
    }

    private String cleanIdentifier(String id) {
        if (id == null) return null;
        return id.trim()
                .replaceAll("^[`\"'\\[]+|[`\"'\\]]+$", "")
                .trim();
    }

    private List<RawStatement> splitStatements(String sql) {
        List<RawStatement> statements = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        int currentStartLine = 1;
        int currentLine = 1;
        boolean inString = false;
        char stringChar = 0;
        boolean inDollarQuote = false;

        char[] chars = sql.toCharArray();
        for (int i = 0; i < chars.length; i++) {
            char c = chars[i];
            if (c == '\n') {
                currentLine++;
            }

            if (!inDollarQuote && (c == '\'' || c == '"') && (i == 0 || chars[i - 1] != '\\')) {
                if (!inString) {
                    inString = true;
                    stringChar = c;
                } else if (stringChar == c) {
                    inString = false;
                }
            } else if (!inString && c == '$' && i + 1 < chars.length && chars[i + 1] == '$') {
                inDollarQuote = !inDollarQuote;
                current.append(c);
                current.append(chars[++i]);
                continue;
            }

            if (c == ';' && !inString && !inDollarQuote) {
                String stmt = current.toString().trim();
                if (!stmt.isEmpty()) {
                    statements.add(new RawStatement(stmt, currentStartLine));
                }
                current.setLength(0);
                currentStartLine = currentLine;
            } else {
                if (current.length() == 0 && Character.isWhitespace(c)) {
                    currentStartLine = currentLine;
                } else {
                    current.append(c);
                }
            }
        }

        String remaining = current.toString().trim();
        if (!remaining.isEmpty()) {
            statements.add(new RawStatement(remaining, currentStartLine));
        }

        return statements;
    }

    private record RawStatement(String text, int lineNumber) {}
}
