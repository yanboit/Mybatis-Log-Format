package wang.imold.mlf;

import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.PlatformDataKeys;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.SelectionModel;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.progress.Task;
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

        // ==============================
        // 🔥 这里改成异步，绝对不卡 UI
        // ==============================
        ProgressManager.getInstance().run(new Task.Backgroundable(null, "正在调用 AI 格式化 SQL...") {
            @Override
            public void run(@NotNull ProgressIndicator indicator) {
                AiFormat aiFormat = new AiFormat();
                String formattedSql;

                try {
                    formattedSql = aiFormat.callDify(rawSql);
                } catch (Exception ex) {
                    formattedSql = "请求失败：" + ex.getMessage();
                }

                // ==============================
                // ✅ 回到 UI 线程显示结果
                // ==============================
                String finalFormattedSql = formattedSql;
                ApplicationManager.getApplication().invokeLater(() -> {
                    MlfUI.showFormattedLog(finalFormattedSql);
                });
            }
        });
    }
    @Override
    public void update(@NotNull AnActionEvent e) {
        Editor editor = e.getData(PlatformDataKeys.EDITOR);
        boolean isVisible = editor != null && editor.getSelectionModel().hasSelection();
        e.getPresentation().setVisible(isVisible);
    }
}