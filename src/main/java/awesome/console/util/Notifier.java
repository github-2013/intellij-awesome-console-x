package awesome.console.util;

import com.intellij.notification.Notification;
import com.intellij.notification.NotificationGroup;
import com.intellij.notification.NotificationGroupManager;
import com.intellij.notification.NotificationType;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import java.util.Collection;
import java.util.List;
import org.jetbrains.annotations.NotNull;

/**
 * 通知工具类
 * 提供IntelliJ IDEA通知功能的封装
 */
public class Notifier {
    
    private static final Logger LOG = Logger.getInstance(Notifier.class);

    /** 通知组ID - 必须与 plugin.xml 中声明的 id 一致 */
    private static final String NOTIFICATION_GROUP_ID = "Awesome Console X";

    /**
     * 获取通知组（延迟初始化）
     * 避免在类静态初始化时调用服务
     * 
     * @return 通知组实例，如果未找到则返回 null
     */
    private static NotificationGroup getNotificationGroup() {
        NotificationGroup group = NotificationGroupManager.getInstance()
                .getNotificationGroup(NOTIFICATION_GROUP_ID);
        
        if (group == null) {
            // 如果通知组未注册，记录错误日志
            // 这通常表示 plugin.xml 配置有问题
            LOG.error("NotificationGroup '" + NOTIFICATION_GROUP_ID + "' not found. " +
                    "Please check plugin.xml configuration.");
        }
        
        return group;
    }

    /**
     * 发送信息类型的通知
     * 
     * @param project 项目对象
     * @param title 通知标题
     * @param message 通知内容
     * @param actions 通知动作按钮
     */
    public static void notify(Project project, @NotNull String title, @NotNull String message, @NotNull AnAction... actions) {
        notify(project, title, message, NotificationType.INFORMATION, actions);
    }

    /**
     * 发送指定类型的通知
     * 
     * @param project 项目对象
     * @param title 通知标题
     * @param message 通知内容
     * @param type 通知类型（信息/警告/错误等）
     * @param actions 通知动作按钮
     */
    public static void notify(Project project, @NotNull String title, @NotNull String message, NotificationType type, @NotNull AnAction... actions) {
        NotificationGroup notificationGroup = getNotificationGroup();
        
        // 如果通知组未找到，降级处理：仅记录日志
        if (notificationGroup == null) {
            LOG.warn("Cannot show notification because NotificationGroup is not available. Title: " + title + ", Message: " + message);
            return;
        }
        
        Notification notification = notificationGroup.createNotification(NOTIFICATION_GROUP_ID + ": " + title, message, type);
        notification.addActions((Collection<? extends AnAction>) List.of(actions));
        notification.setImportant(false).notify(project);
    }
}
