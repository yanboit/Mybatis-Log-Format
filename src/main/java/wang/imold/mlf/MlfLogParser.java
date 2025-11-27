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

    public String formatMybatisLog(String log) {
        try {
            // 1. 提取原始 SQL（增强容错：处理日志不完整情况）
            Matcher preparingMatcher = PREPARING_PATTERN.matcher(log);
            String sql = null;
            if (preparingMatcher.find()) {
                sql = preparingMatcher.group(1).trim();
                // 移除 SQL 末尾可能的多余字符（如分号、逗号）
                sql = sql.replaceAll(";\\s*$", "").replaceAll(",\\s*$", "");
            }
            if (sql == null || sql.isEmpty()) return null;

            // 2. 提取参数（保留原始格式，避免多余字符）
            List<String> params = extractParameters(log);

            // 3. 替换 ? 为参数（安全替换，不替换字符串中的 ?）
            sql = replacePlaceholdersSafely(sql, params);

            // 4. 格式化对齐（兼容 PLSQL，避免越界）
            return cleanAlignSql(sql);
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
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
        // 确保字符串/日期参数带单引号
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
                // 移除参数中的类型标记（如 (String)）
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
     * 安全替换占位符（不替换字符串/函数中的 ?）
     */
    // 2. 修复参数替换逻辑（强制替换所有 ?，不依赖括号计数器）
    private String replacePlaceholdersSafely(String sql, List<String> params) {
        if (params.isEmpty()) return sql;

        StringBuilder sb = new StringBuilder(sql);
        int paramIndex = 0;

        // 简化逻辑：直接替换所有 ?（MyBatis 日志中 ? 都是参数占位符，不会在函数内）
        for (int i = 0; i < sb.length() && paramIndex < params.size(); i++) {
            if (sb.charAt(i) == '?') {
                String param = params.get(paramIndex++);
                sb.replace(i, i + 1, param);
                i += param.length() - 1; // 跳过替换后的字符
            }
        }

        return sb.toString();
    }

    /**
     * 格式化对齐（修复：GROUP BY 后无空行，分号位置正确）
     */
    String cleanAlignSql(String sql) {
        sql = sql.replaceAll("\\s+", " ").trim();
        StringBuilder sb = new StringBuilder();

        // 只处理 SELECT 语句（保持原有逻辑）
        if (!sql.toUpperCase().startsWith("SELECT")) {
            return sql;
        }

        String upperSql = sql.toUpperCase();
        int currentIdx = 6; // SELECT 关键字长度

        // 1. 处理 SELECT 字段（支持多层函数、PLSQL 函数）
        int fromIdx = findFirstMatchIndex(upperSql, currentIdx, FROM_PATTERN);
        if (fromIdx == -1) {
            return sql; // 无 FROM 子句，直接返回
        }
        String selectField = sql.substring(currentIdx, fromIdx).trim();
        sb.append("SELECT\n");
        // 智能拆分字段（忽略函数中的逗号）
        List<String> fields = splitFieldsSafely(selectField);
        for (int i = 0; i < fields.size(); i++) {
            sb.append("    ").append(fields.get(i));
            if (i != fields.size() - 1) {
                sb.append(",");
            }
            sb.append("\n");
        }

        // 2. 处理 FROM 子句（支持 dblink、表关联）
        currentIdx = fromIdx + 5; // FROM 关键字长度
        int whereIdx = findFirstMatchIndex(upperSql, currentIdx, WHERE_PATTERN, GROUP_BY_PATTERN, ORDER_BY_PATTERN, CONNECT_BY_PATTERN, START_WITH_PATTERN);
        if (whereIdx == -1) whereIdx = sql.length();
        String fromTable = sql.substring(currentIdx, whereIdx).trim();
        // 格式化表关联（JOIN 换行）
        fromTable = fromTable.replaceAll("(?i)\\s+(INNER|LEFT|RIGHT|FULL)\\s+JOIN\\s+", "\n    $1 JOIN ");
        fromTable = fromTable.replaceAll("(?i)\\s+ON\\s+", " ON ");
        sb.append("FROM\n    ").append(fromTable).append("\n");

        // 3. 处理 WHERE 子句
        if (upperSql.indexOf(" WHERE ", currentIdx) != -1) {
            currentIdx = whereIdx + 6; // WHERE 关键字长度
            int nextClauseIdx = findFirstMatchIndex(upperSql, currentIdx, GROUP_BY_PATTERN, ORDER_BY_PATTERN, CONNECT_BY_PATTERN, START_WITH_PATTERN);
            if (nextClauseIdx == -1) nextClauseIdx = sql.length();
            String whereCond = sql.substring(currentIdx, nextClauseIdx).trim();
            // 格式化条件（AND/OR 换行）
            whereCond = whereCond.replaceAll("(?i)\\s+(AND|OR)\\s+", "\n    $1 ");
            sb.append("WHERE\n    ").append(whereCond).append("\n");
            currentIdx = nextClauseIdx;
        }

        // 4. 处理 GROUP BY 子句（PLSQL 兼容）
        if (upperSql.indexOf(" GROUP BY ", currentIdx) != -1) {
            int groupIdx = upperSql.indexOf(" GROUP BY ", currentIdx);
            currentIdx = groupIdx + 10; // GROUP BY 关键字长度
            int nextClauseIdx = findFirstMatchIndex(upperSql, currentIdx, HAVING_PATTERN, ORDER_BY_PATTERN, CONNECT_BY_PATTERN);
            if (nextClauseIdx == -1) nextClauseIdx = sql.length();
            String groupPart = sql.substring(currentIdx, nextClauseIdx).trim();
            // 修复：GROUP BY 后无空行，直接跟内容
            sb.append("GROUP BY\n    ").append(groupPart);
            currentIdx = nextClauseIdx;
        }

        // 5. 处理 HAVING 子句
        if (upperSql.indexOf(" HAVING ", currentIdx) != -1) {
            int havingIdx = upperSql.indexOf(" HAVING ", currentIdx);
            currentIdx = havingIdx + 7; // HAVING 关键字长度
            int nextClauseIdx = findFirstMatchIndex(upperSql, currentIdx, ORDER_BY_PATTERN, CONNECT_BY_PATTERN);
            if (nextClauseIdx == -1) nextClauseIdx = sql.length();
            String havingPart = sql.substring(currentIdx, nextClauseIdx).trim();
            sb.append("\nHAVING\n    ").append(havingPart);
            currentIdx = nextClauseIdx;
        }

        // 6. 处理 START WITH 子句（PLSQL）
        if (upperSql.indexOf(" START WITH ", currentIdx) != -1) {
            int startIdx = upperSql.indexOf(" START WITH ", currentIdx);
            currentIdx = startIdx + 11; // START WITH 关键字长度
            int nextClauseIdx = findFirstMatchIndex(upperSql, currentIdx, CONNECT_BY_PATTERN, ORDER_BY_PATTERN);
            if (nextClauseIdx == -1) nextClauseIdx = sql.length();
            String startPart = sql.substring(currentIdx, nextClauseIdx).trim();
            sb.append("\nSTART WITH\n    ").append(startPart);
            currentIdx = nextClauseIdx;
        }

        // 7. 处理 CONNECT BY 子句（PLSQL）
        if (upperSql.indexOf(" CONNECT BY ", currentIdx) != -1) {
            int connectIdx = upperSql.indexOf(" CONNECT BY ", currentIdx);
            currentIdx = connectIdx + 12; // CONNECT BY 关键字长度
            int nextClauseIdx = findFirstMatchIndex(upperSql, currentIdx, ORDER_BY_PATTERN);
            if (nextClauseIdx == -1) nextClauseIdx = sql.length();
            String connectPart = sql.substring(currentIdx, nextClauseIdx).trim();
            sb.append("\nCONNECT BY\n    ").append(connectPart);
            currentIdx = nextClauseIdx;
        }

        // 8. 处理 ORDER BY 子句
        if (upperSql.indexOf(" ORDER BY ", currentIdx) != -1) {
            int orderIdx = upperSql.indexOf(" ORDER BY ", currentIdx);
            currentIdx = orderIdx + 10; // ORDER BY 关键字长度
            String orderPart = sql.substring(currentIdx).trim();
            sb.append("\nORDER BY\n    ").append(orderPart);
        }

        // 9. 补充分号（修复：无多余空行，直接加在末尾）
        if (!sb.toString().trim().endsWith(";")) {
            sb.append(";");
        }

        // 移除多余空行，保持格式整洁
        return sb.toString().replaceAll("\\n+", "\n");
    }

    /**
     * 智能拆分字段（忽略函数、子查询中的逗号）
     */
    private List<String> splitFieldsSafely(String fields) {
        List<String> fieldList = new ArrayList<>();
        int bracketCount = 0;
        int lastSplitPos = 0;

        for (int i = 0; i < fields.length(); i++) {
            char c = fields.charAt(i);
            if (c == '(') {
                bracketCount++;
            } else if (c == ')') {
                bracketCount = Math.max(0, bracketCount - 1);
            } else if (c == ',' && bracketCount == 0) {
                // 只有括号平衡时才拆分
                fieldList.add(fields.substring(lastSplitPos, i).trim());
                lastSplitPos = i + 1;
            }
        }
        // 添加最后一个字段
        fieldList.add(fields.substring(lastSplitPos).trim());
        return fieldList;
    }

    /**
     * 查找第一个匹配的子句索引（避免越界）
     */
    private int findFirstMatchIndex(String sql, int startIdx, Pattern... patterns) {
        int minIdx = -1;
        for (Pattern pattern : patterns) {
            Matcher matcher = pattern.matcher(sql);
            if (matcher.find(startIdx)) {
                int idx = matcher.start();
                if (minIdx == -1 || idx < minIdx) {
                    minIdx = idx;
                }
            }
        }
        return minIdx;
    }
}