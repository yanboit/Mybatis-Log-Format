package wang.imold.mlf;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class MlfLogParser {

    private static final Pattern PREPARING_PATTERN = Pattern.compile("Preparing:\\s*(.+?)(?=\\n|Parameters:)", Pattern.DOTALL);
    private static final Pattern PARAMETERS_PATTERN = Pattern.compile("Parameters:\\s*(.+)", Pattern.DOTALL);
    private static final Pattern PARAM_ITEM_PATTERN = Pattern.compile("([^,]+?)\\s*\\([^)]+\\)", Pattern.DOTALL);

    public String formatMybatisLog(String log) {
        // 1. 提取原始 SQL
        Matcher preparingMatcher = PREPARING_PATTERN.matcher(log);
        String sql = null;
        if (preparingMatcher.find()) {
            sql = preparingMatcher.group(1).trim();
        }
        if (sql == null || sql.isEmpty()) return null;

        // 2. 提取参数（保留双引号）
        Matcher paramsMatcher = PARAMETERS_PATTERN.matcher(log);
        List<String> params = new ArrayList<>();
        if (paramsMatcher.find()) {
            String paramsStr = paramsMatcher.group(1).trim();
            Matcher itemMatcher = PARAM_ITEM_PATTERN.matcher(paramsStr);
            while (itemMatcher.find()) {
                String param = itemMatcher.group(1).trim();
                params.add(param);
            }
        }

        // 3. 替换 ? 为参数
        sql = replacePlaceholders(sql, params);

        // 4. 格式化对齐（彻底避免重复）
        return cleanAlignSql(sql);
    }

    private String replacePlaceholders(String sql, List<String> params) {
        if (params.isEmpty()) return sql;
        StringBuilder sb = new StringBuilder(sql);
        for (String param : params) {
            int pos = sb.indexOf("?");
            if (pos == -1) break;
            sb.replace(pos, pos + 1, param);
        }
        return sb.toString();
    }

    /**
     * 彻底干净的格式对齐（分步截取，无重复）
     */
    private String cleanAlignSql(String sql) {
        sql = sql.replaceAll("\\s+", " ").trim();
        StringBuilder sb = new StringBuilder();

        // 处理 SELECT 语句
        if (sql.toUpperCase().startsWith("SELECT ")) {
            String upperSql = sql.toUpperCase();
            // 1. 截取 SELECT 字段
            int fromIdx = upperSql.indexOf(" FROM ");
            String selectField = sql.substring(7, fromIdx).trim(); // 去掉 "SELECT "
            sb.append("SELECT\n");
            // 拆分字段并缩进
            String[] fields = selectField.split("\\s*,\\s*");
            for (int i = 0; i < fields.length; i++) {
                sb.append("    ").append(fields[i]);
                if (i != fields.length - 1) sb.append(",");
                sb.append("\n");
            }

            // 2. 截取 FROM 表名
            int whereIdx = upperSql.indexOf(" WHERE ", fromIdx);
            String fromTable = sql.substring(fromIdx + 6, whereIdx).trim(); // 去掉 " FROM "
            sb.append("FROM\n    ").append(fromTable).append("\n");

            // 3. 截取 WHERE 条件
            int orderIdx = upperSql.indexOf(" ORDER BY ", whereIdx);
            String whereCond = sql.substring(whereIdx + 7, orderIdx).trim(); // 去掉 " WHERE "
            sb.append("WHERE\n    ").append(whereCond).append("\n");

            // 4. 截取 ORDER BY 部分
            String orderPart = sql.substring(orderIdx + 10).trim(); // 去掉 " ORDER BY "
            sb.append("ORDER BY\n    ").append(orderPart).append(";");
        }

        return sb.toString();
    }
}