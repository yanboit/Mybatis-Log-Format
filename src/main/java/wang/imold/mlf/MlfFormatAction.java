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
            Messages.showWarningDialog("未识别到 MyBatis 日志格式！", "解析失败");
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