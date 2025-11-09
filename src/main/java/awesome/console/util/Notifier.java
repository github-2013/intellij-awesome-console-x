package awesome.console.util;

import com.intellij.notification.Notification;
import com.intellij.notification.NotificationGroup;
import com.intellij.notification.NotificationGroupManager;
import com.intellij.notification.NotificationType;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.project.Project;
import java.util.Collection;
import java.util.List;
import org.jetbrains.annotations.NotNull;

/**
 * 通知工具类
 * 提供IntelliJ IDEA通知功能的封装
 */
public class Notifier {

    /** 通知组 */
    public static final NotificationGroup NOTIFICATION_GROUP = NotificationGroupManager.getInstance().getNotificationGroup("Awesome Console X");

    /** 通知组ID */
    public static final String GROUP_ID = NOTIFICATION_GROUP.getDisplayId();

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
        Notification notification = NOTIFICATION_GROUP.createNotification(GROUP_ID + ": " + title, message, type);
        notification.addActions((Collection<? extends AnAction>) List.of(actions));
        notification.setImportant(false).notify(project);
    }
}
