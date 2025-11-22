package awesome.console.config;

import com.intellij.ui.JBColor;

import javax.swing.*;
import javax.swing.plaf.basic.BasicProgressBarUI;
import java.awt.*;

/**
 * 自定义双色进度条UI
 * 墨绿色表示匹配的文件，黄色表示忽略的文件
 */
class DualColorProgressBarUI extends BasicProgressBarUI {
    private int currentMatchedPercentage = 0;
    private int currentIgnoredPercentage = 0;

    /**
     * 更新匹配和忽略文件的百分比
     * @param matchedPercentage 匹配文件占比（0-100）
     * @param ignoredPercentage 忽略文件占比（0-100）
     */
    public void updatePercentages(int matchedPercentage, int ignoredPercentage) {
        this.currentMatchedPercentage = matchedPercentage;
        this.currentIgnoredPercentage = ignoredPercentage;
        if (progressBar != null) {
            progressBar.repaint();
        }
    }
    @Override
    protected void paintDeterminate(Graphics g, JComponent c) {
        if (!(g instanceof Graphics2D)) {
            return;
        }

        Graphics2D g2d = (Graphics2D) g;
        g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

        Insets b = progressBar.getInsets();
        int width = progressBar.getWidth();
        int height = progressBar.getHeight();
        int barRectWidth = width - (b.right + b.left);
        int barRectHeight = height - (b.top + b.bottom);

        if (barRectWidth <= 0 || barRectHeight <= 0) {
            return;
        }

        // 绘制背景（使用组件背景色或JBColor适配主题的灰色）
        g2d.setColor(progressBar.getBackground() != null ? progressBar.getBackground() : 
                new JBColor(new Color(220, 220, 220), new Color(60, 63, 65)));
        g2d.fillRect(b.left, b.top, barRectWidth, barRectHeight);

        // 计算匹配文件和忽略文件的宽度
        int matchedWidth = (barRectWidth * currentMatchedPercentage) / 100;
        int ignoredWidth = (barRectWidth * currentIgnoredPercentage) / 100;

        // 绘制匹配文件部分（墨绿色，亮色主题用深色，暗色主题用亮色）
        if (matchedWidth > 0) {
            g2d.setColor(new JBColor(new Color(0, 100, 63), new Color(76, 175, 80)));
            g2d.fillRect(b.left, b.top, matchedWidth, barRectHeight);
        }

        // 绘制忽略文件部分（黄色，亮色主题用标准黄色，暗色主题用更亮的黄色）
        if (ignoredWidth > 0) {
            g2d.setColor(new JBColor(new Color(255, 193, 7), new Color(255, 235, 59)));
            g2d.fillRect(b.left + matchedWidth, b.top, ignoredWidth, barRectHeight);
        }

        // 绘制边框（使用JBColor自动适配主题）
        g2d.setColor(JBColor.border());
        g2d.drawRect(b.left, b.top, barRectWidth - 1, barRectHeight - 1);

        // 绘制文本（设置文字颜色以适配主题：亮色主题用黑色，暗色主题用白色）
        if (progressBar.isStringPainted()) {
            progressBar.setForeground(new JBColor(Color.BLACK, Color.WHITE));
            paintString(g, b.left, b.top, barRectWidth, barRectHeight, 0, b);
        }
    }
}
