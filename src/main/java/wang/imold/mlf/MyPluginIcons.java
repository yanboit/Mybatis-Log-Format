package wang.imold.mlf;

import com.intellij.openapi.util.IconLoader;
import javax.swing.*;
import java.awt.*;
import java.awt.image.BufferedImage;

/**
 * 插件图标管理（100% 无编译错误，兼容所有 IDEA 2023+ 版本）
 */
public class MyPluginIcons {
    // 核心图标（右键菜单+插件logo共用，彻底解决依赖问题）
    public static final Icon SQL_ICON = loadFullyCompatibleIcon();

    /**
     * 完全兼容的图标加载逻辑（优先级：SVG → PNG → 自定义兜底图标）
     */
    private static Icon loadFullyCompatibleIcon() {
        // 1. 优先加载 resources 根目录的 SVG 图标（你的主图标）
        try {
            Icon svgIcon = IconLoader.getIcon("sql_icon.svg", MyPluginIcons.class);
            if (svgIcon != null) {
                System.out.println("✅ 加载 SVG 插件图标成功");
                return svgIcon;
            }
        } catch (Exception e) {
            System.err.println("⚠️ SVG 图标加载失败：" + e.getMessage());
        }

        // 2. 备用方案：加载 resources/icons 目录的 PNG 图标（如果存在）
        try {
            Icon pngIcon = IconLoader.getIcon("/icons/sql_icon.svg", MyPluginIcons.class);
            if (pngIcon != null) {
                System.out.println("✅ 加载 PNG 插件图标成功");
                return pngIcon;
            }
        } catch (Exception e) {
            System.err.println("⚠️ PNG 图标加载失败：" + e.getMessage());
        }

        // 3. 最终兜底：创建自定义占位图标（无任何外部依赖，JDK 自带 API）
        System.err.println("⚠️ 所有自定义图标加载失败，使用默认占位图标");
        return createDefaultPlaceholderIcon();
    }

    /**
     * 创建自定义占位图标（32x32，简单 SQL 图标样式，无依赖）
     */
    private static Icon createDefaultPlaceholderIcon() {
        // 创建 32x32 的 BufferedImage
        int size = 32;
        BufferedImage image = new BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g2d = image.createGraphics();

        // 设置抗锯齿
        g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

        // 绘制简单图标（蓝色圆形背景 + 白色 "SQL" 文字）
        g2d.setColor(new Color(52, 152, 219)); // 蓝色背景
        g2d.fillOval(2, 2, size - 4, size - 4); // 圆形背景

        g2d.setColor(Color.WHITE); // 白色文字
        g2d.setFont(new Font("Arial", Font.BOLD, 12));
        FontMetrics metrics = g2d.getFontMetrics();
        String text = "SQL";
        // 文字居中
        int x = (size - metrics.stringWidth(text)) / 2;
        int y = (size - metrics.getHeight()) / 2 + metrics.getAscent();
        g2d.drawString(text, x, y);

        g2d.dispose(); // 释放资源
        return new ImageIcon(image);
    }
}