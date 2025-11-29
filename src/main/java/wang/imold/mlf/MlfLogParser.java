package wang.imold.mlf;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class MlfLogParser {

    private static final Pattern PREPARING_PATTERN = Pattern.compile("(?i)Preparing:\\s*(.+?)(?=\\n|Parameters:|$)", Pattern.DOTALL | Pattern.CASE_INSENSITIVE);
    private static final Pattern PARAMETERS_PATTERN = Pattern.compile("(?i)Parameters:\\s*(.+)", Pattern.DOTALL | Pattern.CASE_INSENSITIVE);
    private static final Pattern PARAM_ITEM_PATTERN = Pattern.compile("([^,()]+?)\\s*\\([^)]+\\)", Pattern.DOTALL);
    private static final Pattern PARAM_ITEM_BACKUP_PATTERN = Pattern.compile("([^,]+?)\\s*,\\s*|([^,]+?)$", Pattern.DOTALL);
    private static final String SUB_QUERY_PLACEHOLDER_PREFIX = "##SUB_QUERY_";
    private static final String SUB_QUERY_PLACEHOLDER_SUFFIX = "##";

    // 匹配字段前的注释（用于修复单行混排）
    private static final Pattern SPLIT_COMMENT_CODE_PATTERN = Pattern.compile("^(\\s*--.*?)\\s+([A-Za-z0-9_\"\\(].*)$", Pattern.DOTALL);

    // 需要大写的常用关键字和函数
    private static final String[] KEYWORDS_TO_UPPER = {
            "count", "sum", "avg", "min", "max", "decode", "nvl", "to_date", "to_char",
            "exists", "distinct", "as", "in", "null", "is", "like", "between", "not", "asc", "desc"
    };

    public String formatMybatisLog(String log) {
        try {
            String sql = extractPureSqlFromLog(log);
            if (sql == null || sql.isEmpty()) return null;

            List<String> params = extractParameters(log);
            String executableSql = replacePlaceholdersSafely(sql, params);

            return formatRawSql(executableSql);
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }

    public String formatRawSql(String sql) {
        return formatSqlRecursive(sql, 0);
    }

    public String extractPureSqlFromLog(String log) {
        Matcher preparingMatcher = PREPARING_PATTERN.matcher(log);
        if (preparingMatcher.find()) {
            String sql = preparingMatcher.group(1).trim();
            return sql.replaceAll("[;,\\s]+$", "");
        }
        return null;
    }

    private String formatSqlRecursive(String sql, int indentLevel) {
        String cleanSql = sql.trim();

        // 移除最外层包裹
        boolean isWrapped = false;
        if (cleanSql.startsWith("(") && cleanSql.endsWith(")") && isBalancedWrapper(cleanSql)) {
            cleanSql = cleanSql.substring(1, cleanSql.length() - 1).trim();
            isWrapped = true;
        }

        // 1. 保护子查询
        Map<String, String> subQueryMap = new HashMap<>();
        String maskedSql = maskSubQueries(cleanSql, subQueryMap);

        // 2. 格式化当前层级
        String formatted = formatSingleLevel(maskedSql, indentLevel);

        // 3. 还原子查询
        for (Map.Entry<String, String> entry : subQueryMap.entrySet()) {
            String placeholder = entry.getKey();
            String rawSubQuery = entry.getValue();

            String bracketIndent = getIndent(indentLevel + 1);
            String formattedSubQuery = formatSqlRecursive(rawSubQuery, indentLevel + 2);

            String replacement = "(\n" + formattedSubQuery + "\n" + bracketIndent + ")";
            formatted = formatted.replace(placeholder, replacement);
        }

        // 4. 全局关键字大写优化
        formatted = uppercaseKeywordsSafely(formatted);

        return formatted;
    }

    private String formatSingleLevel(String sql, int indentLevel) {
        String normalized = sql.replace("\r\n", "\n").replace("\r", "\n");
        String processed = normalized.replaceAll("[ \\t\\x0B\\f]+", " ");

        StringBuilder sb = new StringBuilder();
        String indent = getIndent(indentLevel);
        String subIndent = getIndent(indentLevel + 1);

        String upper = processed.toUpperCase();

        int selectIdx = upper.indexOf("SELECT");
        int fromIdx = findKeywordIndex(processed, "FROM");
        int whereIdx = findKeywordIndex(processed, "WHERE");

        if (selectIdx == -1) return indent + processed;

        // --- 1. SELECT ---
        sb.append(indent).append("SELECT\n");
        int selectEnd = (fromIdx != -1) ? fromIdx : (whereIdx != -1 ? whereIdx : processed.length());

        String selectPart = processed.substring(selectIdx + 6, selectEnd).trim();
        List<FieldItem> fields = splitSelectFields(selectPart);

        for (int i = 0; i < fields.size(); i++) {
            FieldItem item = fields.get(i);
            sb.append(subIndent).append(item.code);
            if (i < fields.size() - 1) sb.append(",");
            if (item.comment != null && !item.comment.isEmpty()) sb.append(" ").append(item.comment);
            sb.append("\n");
        }

        // --- 2. FROM ---
        if (fromIdx != -1) {
            int fromEnd = (whereIdx != -1) ? whereIdx : processed.length();
            String fromPart = processed.substring(fromIdx + 4, fromEnd).trim();
            sb.append(indent).append("FROM\n");

            fromPart = fromPart.replaceAll("(?i)\\s(LEFT|RIGHT|INNER|FULL|OUTER|CROSS)\\s+JOIN\\s", "\n" + subIndent + "$1 JOIN ");
            fromPart = fromPart.replaceAll("(?i)\\sON\\s", " ON ");

            String[] lines = fromPart.split("\n");
            for (int i = 0; i < lines.length; i++) {
                String line = lines[i].trim();
                if (!line.isEmpty()) {
                    sb.append(subIndent).append(line);
                    if (i < lines.length - 1) {
                        sb.append("\n");
                    }
                }
            }
        }

        // --- 3. WHERE / GROUP BY ... ---
        if (whereIdx != -1) {
            String wherePart = processed.substring(whereIdx);
            String[] keywords = {"WHERE", "GROUP BY", "HAVING", "ORDER BY", "CONNECT BY", "START WITH"};

            String currentPart = wherePart;
            for (String kw : keywords) {
                // 匹配关键字并处理缩进
                Matcher matcher = Pattern.compile("(?i)\\b" + kw.replace(" ", "\\s+") + "\\b\\s*").matcher(currentPart);
                if (matcher.find()) {
                    String foundKw = matcher.group().trim();
                    currentPart = matcher.replaceFirst("\n" + indent + foundKw.toUpperCase() + "\n" + subIndent);
                }
            }

            // 【核心修复】AND / OR 换行对齐
            // 必须加上 \\b (单词边界)，否则会匹配到 ORDER BY 里的 OR，或 ORG_IDS 里的 OR
            currentPart = currentPart.replaceAll("(?i)\\s+AND\\b\\s*", "\n" + subIndent + "AND ");
            currentPart = currentPart.replaceAll("(?i)\\s+OR\\b\\s*", "\n" + subIndent + "OR ");

            sb.append(currentPart.replaceAll("\\s+$", ""));
        }

        return sb.toString();
    }

    // --- 字段处理逻辑 ---
    private static class FieldItem {
        String code;
        String comment;
        FieldItem(String code, String comment) {
            this.code = code;
            this.comment = comment;
        }
    }

    private List<FieldItem> splitSelectFields(String selectPart) {
        List<String> rawParts = splitByComma(selectPart);
        List<FieldItem> result = new ArrayList<>();
        for (String part : rawParts) {
            part = part.trim();
            if (part.isEmpty()) continue;
            String preComment = "";
            String realCode = part;
            if (part.startsWith("--")) {
                Matcher m = SPLIT_COMMENT_CODE_PATTERN.matcher(part);
                if (m.find()) {
                    preComment = m.group(1).trim();
                    realCode = m.group(2).trim();
                } else {
                    preComment = part;
                    realCode = "";
                }
            }
            if (!preComment.isEmpty() && !result.isEmpty()) {
                FieldItem prev = result.get(result.size() - 1);
                prev.comment = (prev.comment + " " + preComment).trim();
            }
            if (realCode.isEmpty()) continue;
            result.add(parseFieldItem(realCode));
        }
        return result;
    }

    private List<String> splitByComma(String text) {
        List<String> parts = new ArrayList<>();
        StringBuilder sb = new StringBuilder();
        int parenDepth = 0;
        for (char c : text.toCharArray()) {
            if (c == '(') parenDepth++;
            else if (c == ')') parenDepth--;
            if (c == ',' && parenDepth == 0) {
                parts.add(sb.toString());
                sb.setLength(0);
            } else {
                sb.append(c);
            }
        }
        if (sb.length() > 0) parts.add(sb.toString());
        return parts;
    }

    private FieldItem parseFieldItem(String raw) {
        StringBuilder code = new StringBuilder();
        StringBuilder comment = new StringBuilder();
        int len = raw.length();
        for (int i = 0; i < len; i++) {
            char c = raw.charAt(i);
            if (c == '-' && i + 1 < len && raw.charAt(i + 1) == '-') {
                comment.append(raw.substring(i));
                break;
            } else {
                code.append(c);
            }
        }
        return new FieldItem(code.toString().trim(), comment.toString().trim());
    }

    // --- 辅助方法 ---
    private String uppercaseKeywordsSafely(String sql) {
        String res = sql;
        for (String kw : KEYWORDS_TO_UPPER) {
            res = res.replaceAll("(?i)\\b" + kw + "\\b", kw.toUpperCase());
        }
        return res;
    }

    private String maskSubQueries(String sql, Map<String, String> subQueryMap) {
        StringBuilder sb = new StringBuilder();
        StringBuilder buffer = new StringBuilder();
        int parenDepth = 0;
        int subQueryCount = 0;
        boolean buffering = false;
        char[] chars = sql.toCharArray();
        for (int i = 0; i < chars.length; i++) {
            char c = chars[i];
            if (!buffering) {
                if (c == '(') {
                    if (isSelectStart(sql, i + 1)) {
                        buffering = true;
                        parenDepth = 1;
                        buffer.setLength(0);
                        continue;
                    }
                }
                sb.append(c);
            } else {
                if (c == '(') parenDepth++;
                else if (c == ')') parenDepth--;
                if (parenDepth == 0) {
                    String key = SUB_QUERY_PLACEHOLDER_PREFIX + (subQueryCount++) + SUB_QUERY_PLACEHOLDER_SUFFIX;
                    subQueryMap.put(key, buffer.toString());
                    sb.append(key);
                    buffering = false;
                } else {
                    buffer.append(c);
                }
            }
        }
        return sb.toString();
    }

    private boolean isSelectStart(String sql, int idx) {
        while (idx < sql.length() && Character.isWhitespace(sql.charAt(idx))) {
            idx++;
        }
        if (idx + 6 <= sql.length()) {
            return sql.substring(idx, idx + 6).equalsIgnoreCase("SELECT");
        }
        return false;
    }

    private int findKeywordIndex(String sql, String keyword) {
        Matcher m = Pattern.compile("(?i)(^|\\s|\\))" + keyword + "(\\s|\\()").matcher(sql);
        if (m.find()) {
            return m.start() + m.group(1).length();
        }
        return -1;
    }

    private String getIndent(int level) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < level; i++) sb.append("    ");
        return sb.toString();
    }

    private boolean isBalancedWrapper(String sql) {
        int depth = 0;
        for (int i = 0; i < sql.length(); i++) {
            char c = sql.charAt(i);
            if (c == '(') depth++;
            else if (c == ')') depth--;
            if (depth == 0 && i < sql.length() - 1) return false;
        }
        return depth == 0;
    }

    private List<String> extractParameters(String log) {
        List<String> params = new ArrayList<>();
        Matcher paramsMatcher = PARAMETERS_PATTERN.matcher(log);
        if (!paramsMatcher.find()) return params;
        String paramsStr = paramsMatcher.group(1).trim();
        Matcher itemMatcher = PARAM_ITEM_PATTERN.matcher(paramsStr);
        while (itemMatcher.find()) {
            addParam(params, itemMatcher.group(1));
        }
        if (params.isEmpty()) {
            Matcher backupMatcher = PARAM_ITEM_BACKUP_PATTERN.matcher(paramsStr);
            while (backupMatcher.find()) {
                String val = backupMatcher.group(1) != null ? backupMatcher.group(1) : backupMatcher.group(2);
                addParam(params, val);
            }
        }
        return params;
    }

    private void addParam(List<String> params, String param) {
        if (param == null) return;
        param = param.trim();
        param = param.replaceAll("\\([^)]+\\)$", "").trim();
        if (!param.startsWith("'") && !param.startsWith("\"") && !param.matches("-?\\d+(\\.\\d+)?") && !"true".equalsIgnoreCase(param) && !"false".equalsIgnoreCase(param) && !"null".equalsIgnoreCase(param)) {
            param = "'" + param + "'";
        }
        params.add(param);
    }

    private String replacePlaceholdersSafely(String sql, List<String> params) {
        if (params.isEmpty()) return sql;
        StringBuilder sb = new StringBuilder();
        int paramIdx = 0;
        for (int i = 0; i < sql.length(); i++) {
            char c = sql.charAt(i);
            if (c == '?' && paramIdx < params.size()) {
                sb.append(params.get(paramIdx++));
            } else {
                sb.append(c);
            }
        }
        return sb.toString();
    }
}