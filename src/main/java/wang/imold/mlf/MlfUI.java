package wang.imold.mlf;

import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.EditorFactory;
import com.intellij.openapi.editor.colors.EditorColorsManager;
import com.intellij.openapi.editor.ex.EditorEx;
import com.intellij.openapi.editor.highlighter.EditorHighlighter;
import com.intellij.openapi.editor.highlighter.EditorHighlighterFactory;
import com.intellij.openapi.fileTypes.FileTypeManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectManager;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.ui.components.JBScrollPane;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.awt.datatransfer.StringSelection;
import java.awt.event.ActionEvent;
import static javax.swing.AbstractAction.NAME;

public class MlfUI extends DialogWrapper {

    private final String sql;
    private Editor editor;

    private MlfUI(String sql) {
        super(true);
        this.sql = sql;
        setTitle("MyBatis SQL 格式化结果");
        init();
        // 自适应窗口大小（根据 SQL 长度调整）
        int lineCount = sql.split("\n").length;
        setSize(900, Math.min(600, 40 * lineCount)); // 最大高度 600
    }

    public static void showFormattedLog(String sql) {
        MlfUI ui = new MlfUI(sql);
        ui.show();
    }

    @Nullable
    @Override
    protected JComponent createCenterPanel() {
        Project project = ProjectManager.getInstance().getDefaultProject();
        if (project == null) return new JLabel("无法加载项目");

        EditorFactory factory = EditorFactory.getInstance();
        Document doc = factory.createDocument(sql);
        editor = factory.createEditor(doc, project);
        EditorEx editorEx = (EditorEx) editor;

        // 强制 SQL 高亮
        EditorHighlighter highlighter = EditorHighlighterFactory.getInstance()
                .createEditorHighlighter(
                        FileTypeManager.getInstance().getFileTypeByExtension("sql"),
                        EditorColorsManager.getInstance().getGlobalScheme(),
                        project
                );
        editorEx.setHighlighter(highlighter);

        editor.getSettings().setLineNumbersShown(false);
        editor.getDocument().setReadOnly(true);

        JBScrollPane scrollPane = new JBScrollPane(editor.getComponent());
        scrollPane.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_AS_NEEDED);
        scrollPane.setVerticalScrollBarPolicy(JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED);
        return scrollPane;
    }

    @NotNull
    @Override
    protected Action[] createActions() {
        Action copyAction = new AbstractAction("复制SQL") {
            @Override
            public void actionPerformed(ActionEvent e) {
                String plainSql = editor.getDocument().getText();
                Toolkit.getDefaultToolkit().getSystemClipboard().setContents(
                        new StringSelection(plainSql), null
                );
                JOptionPane.showMessageDialog(null, "已复制SQL", "提示", JOptionPane.INFORMATION_MESSAGE);
            }
        };

        Action closeAction = getCancelAction();
        closeAction.putValue(NAME, "关闭");
        return new Action[]{copyAction, closeAction};
    }

    @Override
    public void dispose() {
        if (editor != null) EditorFactory.getInstance().releaseEditor(editor);
        super.dispose();
    }
}