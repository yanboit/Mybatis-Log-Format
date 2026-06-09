package wang.imold.mlf;

import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.PlatformDataKeys;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.SelectionModel;
import com.intellij.openapi.ui.Messages;
import org.jetbrains.annotations.NotNull;
import wang.imold.mlf.util.AiFormat;

public class MlfFormatAction extends AnAction {

    @Override
    public void actionPerformed(@NotNull AnActionEvent e) {
        Editor editor = e.getData(PlatformDataKeys.EDITOR);
        if (editor == null) {
            Messages.showWarningDialog("请在控制台选中文本！", "提示");
            return;
        }

        SelectionModel selectionModel = editor.getSelectionModel();
        String selectedText = selectionModel.getSelectedText();
        if (selectedText == null || selectedText.trim().isEmpty()) {
            Messages.showWarningDialog("请选中有效的 MyBatis 日志文本！", "提示");
            return;
        }

        MlfLogParser parser = new MlfLogParser();
        String rawSql = parser.formatMybatisLog(selectedText);
//
//        if (formattedSql == null) {
//            if (!selectedText.toLowerCase().contains("preparing:")) {
//                Messages.showErrorDialog("未识别到 Preparing 关键字，请选中完整的 MyBatis 日志！", "解析失败");
//            } else if (!selectedText.toLowerCase().contains("parameters:")) {
//                String pureSql = parser.extractPureSqlFromLog(selectedText);
//                if (pureSql != null && !pureSql.isEmpty()) {
//                    formattedSql = parser.formatRawSql(pureSql);
//                    if (formattedSql != null) {
//                        MlfUI.showFormattedLog(formattedSql);
//                        return;
//                    }
//                }
//                Messages.showErrorDialog("未识别到 Parameters 且无法解析 SQL！", "解析失败");
//            } else {
//                Messages.showErrorDialog("SQL 格式化失败！", "解析失败");
//            }
//            return;
//        }
        AiFormat aiFormat = new AiFormat();
//        String testStr = "==>  Preparing: SELECT id, patient_name, patient_id, gender, age, phone, department, bed_no, check_item, check_result, reference_range, unit, check_date, doctor, check_department, sample_type, diagnostic_note, status, create_time, update_time FROM t_labresult WHERE age >= ? AND check_date BETWEEN ? AND ? AND check_result LIKE ? AND sample_type = ? AND status IN (?,?) AND department = ? AND diagnostic_note IS NOT NULL ORDER BY check_date DESC LIMIT ?\n" +
//                "==> Parameters: 50(Integer), 2025-01-01 00:00:00(Timestamp), 2025-12-31 23:59:59(Timestamp), %阳性%(String), 血液(String), 已审核(String), 已完成(String), 内分泌科(String), 20(Integer)\n" +
//                "<==      Total: 18";
        String formattedSql = "";
        try{
            formattedSql = aiFormat.callDify(rawSql);
        }catch (Exception ex) {
            formattedSql = "请求失败：" + ex.getMessage();
        }

        MlfUI.showFormattedLog(formattedSql);
    }

    @Override
    public void update(@NotNull AnActionEvent e) {
        Editor editor = e.getData(PlatformDataKeys.EDITOR);
        boolean isVisible = editor != null && editor.getSelectionModel().hasSelection();
        e.getPresentation().setVisible(isVisible);
    }
}