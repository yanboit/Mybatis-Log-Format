package wang.imold.mlf;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class MlfLogParser {

    // 关键修改：修复参数提取正则（支持带空格的参数，如日期）
    private static final Pattern PARAM_ITEM_PATTERN = Pattern.compile("([^,()]+?)\\s*\\([^)]+\\)", Pattern.DOTALL);
    // 新增：备用参数提取正则（如果上面的正则失败，用这个兜底）
    private static final Pattern PARAM_ITEM_BACKUP_PATTERN = Pattern.compile("([^,]+?)\\s*,\\s*|([^,]+?)$", Pattern.DOTALL);

    // 增强正则：兼容 PLSQL，忽略大小写，支持换行
    private static final Pattern PREPARING_PATTERN = Pattern.compile("(?i)Preparing:\\s*(.+?)(?=\\n|Parameters:|$)", Pattern.DOTALL | Pattern.CASE_INSENSITIVE);
    private static final Pattern PARAMETERS_PATTERN = Pattern.compile("(?i)Parameters:\\s*(.+)", Pattern.DOTALL | Pattern.CASE_INSENSITIVE);

    // 扩展 SQL 子句正则（支持 PLSQL）
    private static final Pattern FROM_PATTERN = Pattern.compile("(?i)\\s+FROM\\s+", Pattern.CASE_INSENSITIVE);
    private static final Pattern WHERE_PATTERN = Pattern.compile("(?i)\\s+WHERE\\s+", Pattern.CASE_INSENSITIVE);
    private static final Pattern GROUP_BY_PATTERN = Pattern.compile("(?i)\\s+GROUP\\s+BY\\s+", Pattern.CASE_INSENSITIVE);
    private static final Pattern HAVING_PATTERN = Pattern.compile("(?i)\\s+HAVING\\s+", Pattern.CASE_INSENSITIVE);
    private static final Pattern ORDER_BY_PATTERN = Pattern.compile("(?i)\\s+ORDER\\s+BY\\s+", Pattern.CASE_INSENSITIVE);
    private static final Pattern CONNECT_BY_PATTERN = Pattern.compile("(?i)\\s+CONNECT\\s+BY\\s+", Pattern.CASE_INSENSITIVE);
    private static final Pattern START_WITH_PATTERN = Pattern.compile("(?i)\\s+START\\s+WITH\\s+", Pattern.CASE_INSENSITIVE);

    // 修复：匹配「字段+注释」整体（确保注释和字段严格对应）
    private static final Pattern FIELD_WITH_COMMENT_PATTERN = Pattern.compile("([A-Za-z0-9_\\.]+)\\s*(--.+?)?(?=,|\\s+FROM|$)", Pattern.DOTALL | Pattern.CASE_INSENSITIVE);
    // 匹配子查询
    private static final Pattern SUB_SELECT_PATTERN = Pattern.compile("\\(\\s*(?i)SELECT\\s+.+?\\s*\\)", Pattern.DOTALL | Pattern.CASE_INSENSITIVE);

    public String formatMybatisLog(String log) {
        try {
            // 1. 提取原始 SQL
            Matcher preparingMatcher = PREPARING_PATTERN.matcher(log);
            String sql = null;
            if (preparingMatcher.find()) {
                sql = preparingMatcher.group(1).trim();
                sql = sql.replaceAll(";\\s*$", "").replaceAll(",\\s*$", "");
            }
            if (sql == null || sql.isEmpty()) return null;

            // 2. 提取参数并替换 ?
            List<String> params = extractParameters(log);
            String pureSql = replacePlaceholdersSafely(sql, params);

            // 3. 格式化所有查询（主查询+子查询）
            pureSql = formatAllQueries(pureSql);

            // 4. 最终格式化对齐
            return cleanAlignSql(pureSql);
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }

    /**
     * 格式化所有查询（主查询+子查询）：提取「字段+注释」整体
     */
    private String formatAllQueries(String sql) {
        // 递归处理子查询
        Matcher subSelectMatcher = SUB_SELECT_PATTERN.matcher(sql);
        StringBuffer sb = new StringBuffer();
        while (subSelectMatcher.find()) {
            String subQuery = subSelectMatcher.group().trim();
            String formattedSub = formatSingleQuery(subQuery);
            subSelectMatcher.appendReplacement(sb, Matcher.quoteReplacement(formattedSub));
        }
        subSelectMatcher.appendTail(sb);
        // 处理主查询
        return formatSingleQuery(sb.toString());
    }

    /**
     * 格式化单个查询：提取「字段+注释」，避免注释截断
     */
    private String formatSingleQuery(String query) {
        String pureQuery = query.replaceAll("\\s+", " ").trim();
        if (!pureQuery.toUpperCase().startsWith("SELECT") && !pureQuery.startsWith("(")) {
            return query;
        }
        // 处理子查询的括号
        boolean isSubQuery = pureQuery.startsWith("(");
        if (isSubQuery) {
            pureQuery = pureQuery.substring(1, pureQuery.length() - 1).trim();
        }

        String upperQuery = pureQuery.toUpperCase();
        int fromIdx = upperQuery.indexOf(" FROM ");
        if (fromIdx == -1) {
            return query;
        }

        // 提取「字段+注释」整体（关键修复：避免注释截断）
        String fieldsPart = pureQuery.substring(6, fromIdx).trim();
        List<String> fieldsWithComments = extractFieldsWithComments(fieldsPart);
        String restPart = pureQuery.substring(fromIdx).trim();

        // 构建格式化后的查询
        StringBuilder formatted = new StringBuilder();
        if (isSubQuery) formatted.append("(");
        formatted.append("SELECT\n");
        for (int i = 0; i < fieldsWithComments.size(); i++) {
            formatted.append("    ").append(fieldsWithComments.get(i));
            if (i != fieldsWithComments.size() - 1) formatted.append(",");
            formatted.append("\n");
        }
        formatted.append("FROM ").append(restPart);
        if (isSubQuery) formatted.append(")");

        return formatted.toString();
    }

    /**
     * 提取「字段+注释」整体（确保注释和字段一一对应）
     */
    private List<String> extractFieldsWithComments(String fieldsPart) {
        List<String> result = new ArrayList<>();
        Matcher matcher = FIELD_WITH_COMMENT_PATTERN.matcher(fieldsPart);
        while (matcher.find()) {
            String field = matcher.group(1).trim();
            String comment = matcher.group(2);
            if (comment != null && !comment.trim().isEmpty()) {
                field += " " + comment.trim();
            }
            result.add(field);
        }
        return result;
    }

    public String extractPureSqlFromLog(String log) {
        Matcher preparingMatcher = PREPARING_PATTERN.matcher(log);
        if (preparingMatcher.find()) {
            String sql = preparingMatcher.group(1).trim();
            sql = sql.replaceAll(";\\s*$", "").replaceAll(",\\s*$", "");
            return sql;
        }
        return null;
    }

    /**
     * 处理单个参数（添加单引号、移除多余符号）
     */
    private String processParam(String param) {
        param = param.replaceAll("=>", "").trim();
        if (!param.startsWith("'") && !param.startsWith("\"") && !param.matches("\\d+|true|false|null")) {
            param = "'" + param + "'";
        }
        return param;
    }

    /**
     * 提取参数（修复：移除参数中的类型标记，保留纯值）
     */
    private List<String> extractParameters(String log) {
        List<String> params = new ArrayList<>();
        Matcher paramsMatcher = PARAMETERS_PATTERN.matcher(log);
        if (!paramsMatcher.find()) {
            return params;
        }

        String paramsStr = paramsMatcher.group(1).trim();
        // 先用原有正则提取
        Matcher itemMatcher = PARAM_ITEM_PATTERN.matcher(paramsStr);
        while (itemMatcher.find()) {
            String param = itemMatcher.group(1).trim();
            param = processParam(param);
            if (!param.isEmpty()) {
                params.add(param);
            }
        }

        // 如果提取失败（参数带空格），用备用正则
        if (params.isEmpty()) {
            itemMatcher = PARAM_ITEM_BACKUP_PATTERN.matcher(paramsStr);
            while (itemMatcher.find()) {
                String param = (itemMatcher.group(1) != null ? itemMatcher.group(1) : itemMatcher.group(2)).trim();
                param = param.replaceAll("\\([^)]+\\)", "").trim();
                param = processParam(param);
                if (!param.isEmpty()) {
                    params.add(param);
                }
            }
        }

        return params;
    }

    /**
     * 安全替换占位符（不替换字符串中的 ?）
     */
    private String replacePlaceholdersSafely(String sql, List<String> params) {
        if (params.isEmpty()) return sql;

        StringBuilder sb = new StringBuilder(sql);
        int paramIndex = 0;
        for (int i = 0; i < sb.length() && paramIndex < params.size(); i++) {
            if (sb.charAt(i) == '?') {
                String param = params.get(paramIndex++);
                sb.replace(i, i + 1, param);
                i += param.length() - 1;
            }
        }
        return sb.toString();
    }

    /**
     * 格式化对齐（兼容 PLSQL，避免越界）
     */
    public String cleanAlignSql(String sql) {
        sql = sql.replaceAll("\\s+", " ").trim();
        StringBuilder sb = new StringBuilder();

        if (!sql.toUpperCase().startsWith("SELECT")) {
            return sql;
        }

        String upperSql = sql.toUpperCase();
        int currentIdx = 6;

        // 1. 处理 SELECT 字段
        int fromIdx = findFirstMatchIndex(upperSql, currentIdx, FROM_PATTERN);
        if (fromIdx == -1) {
            return sql;
        }
        String selectField = sql.substring(currentIdx, fromIdx).trim();
        sb.append("SELECT\n");
        List<String> fields = extractFieldsWithComments(selectField);
        for (int i = 0; i < fields.size(); i++) {
            sb.append("    ").append(fields.get(i));
            if (i != fields.size() - 1) sb.append(",");
            sb.append("\n");
        }

        // 2. 处理 FROM 子句
        currentIdx = fromIdx + 5;
        int whereIdx = findFirstMatchIndex(upperSql, currentIdx, WHERE_PATTERN, GROUP_BY_PATTERN, ORDER_BY_PATTERN, CONNECT_BY_PATTERN, START_WITH_PATTERN);
        if (whereIdx == -1) whereIdx = sql.length();
        String fromTable = sql.substring(currentIdx, whereIdx).trim();
        fromTable = fromTable.replaceAll("(?i)\\s+(INNER|LEFT|RIGHT|FULL)\\s+JOIN\\s+", "\n    $1 JOIN ");
        fromTable = fromTable.replaceAll("(?i)\\s+ON\\s+", " ON ");
        sb.append("FROM\n    ").append(fromTable).append("\n");

        // 3. 处理 WHERE 子句
        if (upperSql.indexOf(" WHERE ", currentIdx) != -1) {
            currentIdx = whereIdx + 6;
            int nextClauseIdx = findFirstMatchIndex(upperSql, currentIdx, GROUP_BY_PATTERN, ORDER_BY_PATTERN, CONNECT_BY_PATTERN, START_WITH_PATTERN);
            if (nextClauseIdx == -1) nextClauseIdx = sql.length();
            String whereCond = sql.substring(currentIdx, nextClauseIdx).trim();
            whereCond = whereCond.replaceAll("(?i)\\s+(AND|OR)\\s+", "\n    $1 ");
            sb.append("WHERE\n    ").append(whereCond).append("\n");
            currentIdx = nextClauseIdx;
        }

        // 4. 处理后续子句（GROUP BY/HAVING 等）
        if (upperSql.indexOf(" GROUP BY ", currentIdx) != -1) {
            int groupIdx = upperSql.indexOf(" GROUP BY ", currentIdx);
            currentIdx = groupIdx + 10;
            int nextClauseIdx = findFirstMatchIndex(upperSql, currentIdx, HAVING_PATTERN, ORDER_BY_PATTERN, CONNECT_BY_PATTERN);
            if (nextClauseIdx == -1) nextClauseIdx = sql.length();
            sb.append("GROUP BY\n    ").append(sql.substring(currentIdx, nextClauseIdx).trim());
            currentIdx = nextClauseIdx;
        }
        if (upperSql.indexOf(" HAVING ", currentIdx) != -1) {
            int havingIdx = upperSql.indexOf(" HAVING ", currentIdx);
            currentIdx = havingIdx + 7;
            int nextClauseIdx = findFirstMatchIndex(upperSql, currentIdx, ORDER_BY_PATTERN, CONNECT_BY_PATTERN);
            if (nextClauseIdx == -1) nextClauseIdx = sql.length();
            sb.append("\nHAVING\n    ").append(sql.substring(currentIdx, nextClauseIdx).trim());
            currentIdx = nextClauseIdx;
        }
        if (upperSql.indexOf(" ORDER BY ", currentIdx) != -1) {
            int orderIdx = upperSql.indexOf(" ORDER BY ", currentIdx);
            currentIdx = orderIdx + 10;
            sb.append("\nORDER BY\n    ").append(sql.substring(currentIdx).trim());
        }

        if (!sb.toString().trim().endsWith(";")) sb.append(";");
        return sb.toString().replaceAll("\\n+", "\n");
    }

    /**
     * 查找第一个匹配的子句索引
     */
    private int findFirstMatchIndex(String sql, int startIdx, Pattern... patterns) {
        int minIdx = -1;
        for (Pattern pattern : patterns) {
            Matcher matcher = pattern.matcher(sql);
            if (matcher.find(startIdx)) {
                int idx = matcher.start();
                if (minIdx == -1 || idx < minIdx) minIdx = idx;
            }
        }
        return minIdx;
    }
}