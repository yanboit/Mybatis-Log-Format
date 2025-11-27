package wang.imold.mlf;

import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.PlatformDataKeys;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.SelectionModel;
import com.intellij.openapi.ui.Messages;
import org.jetbrains.annotations.NotNull;

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
        String formattedSql = parser.formatMybatisLog(selectedText);
        if (formattedSql == null) {
            // 区分失败场景，给出详细提示（不直接访问私有常量）
            if (!selectedText.toLowerCase().contains("preparing:")) {
                Messages.showErrorDialog("未识别到 Preparing 关键字，请选中完整的 MyBatis 日志！", "解析失败");
            } else if (!selectedText.toLowerCase().contains("parameters:")) {
                // 无参数时，调用 parser 的方法提取 SQL 并格式化（避免访问私有常量）
                String pureSql = parser.extractPureSqlFromLog(selectedText);
                if (pureSql != null && !pureSql.isEmpty()) {
                    formattedSql = parser.cleanAlignSql(pureSql);
                    if (formattedSql != null) {
                        MlfUI.showFormattedLog(formattedSql);
                        return;
                    }
                }
                Messages.showErrorDialog("未识别到 Parameters 关键字，SQL 格式化失败！", "解析失败");
            } else {
                Messages.showErrorDialog("SQL 格式化失败！可能是语法过于复杂或日志格式不完整。", "解析失败");
            }
            return;
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