package com.ouhuan.oplusassistant.xposed;

import android.app.Binder;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;

import com.ouhuan.oplusassistant.shared.Constants;
import com.ouhuan.oplusassistant.shared.LaunchResult;
import com.ouhuan.oplusassistant.shared.LaunchTarget;

/**
 * 以系统上下文按 Android 标准 Assistant 入口发起调用（开发书 4.1 / 7）。
 * 只负责「尽最大可能正确启动已被 Resolver 判定可用的助手」，
 * 不做任何回退、重试或 UI 确认；SUCCESS 表示系统接受了本次启动请求。
 */
public final class AssistantLauncher {

    /** 启动结果 + 异常摘要。 */
    public static final class LaunchOutcome {
        public final LaunchResult result;
        public final String exceptionType;
        public final String summary;

        LaunchOutcome(LaunchResult result, String exceptionType, String summary) {
            this.result = result;
            this.exceptionType = exceptionType;
            this.summary = summary;
        }
    }

    public LaunchOutcome launch(Context context, LaunchTarget target) {
        ComponentName component = ComponentName.unflattenFromString(target.componentName);
        if (component == null) {
            return new LaunchOutcome(LaunchResult.RESOLVE_FAILED, null,
                "invalid component: " + target.componentName);
        }
        Intent intent = new Intent(Constants.LAUNCH_METHOD_ASSIST.equals(target.launchMethod)
            ? Intent.ACTION_ASSIST
            : Intent.ACTION_VOICE_COMMAND);
        intent.setComponent(component);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK
            | Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED);
        long token = Binder.clearCallingIdentity();
        try {
            context.startActivity(intent);
            return new LaunchOutcome(LaunchResult.SUCCESS, null, null);
        } catch (SecurityException e) {
            String message = e.getMessage() == null ? "" : e.getMessage();
            if (message.toLowerCase(java.util.Locale.US).contains("background")) {
                return new LaunchOutcome(LaunchResult.BACKGROUND_START_DENIED,
                    e.getClass().getSimpleName(), message);
            }
            return new LaunchOutcome(LaunchResult.SECURITY_EXCEPTION,
                e.getClass().getSimpleName(), message);
        } catch (Throwable t) {
            return new LaunchOutcome(LaunchResult.LAUNCH_EXCEPTION,
                t.getClass().getSimpleName(),
                t.getMessage() == null ? t.toString() : t.getMessage());
        } finally {
            Binder.restoreCallingIdentity(token);
        }
    }
}
