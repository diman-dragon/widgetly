package __APP_ID__;

import android.app.Notification;
import android.content.Context;
import android.content.pm.PackageManager;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.service.notification.NotificationListenerService;
import android.service.notification.StatusBarNotification;
import org.json.JSONArray;
import org.json.JSONObject;

/** Считает активные уведомления для блока "уведомления". Тексты хранятся только на устройстве. */
public class NotifListener extends NotificationListenerService {
    private final Handler h = new Handler(Looper.getMainLooper());
    private final Runnable job = this::refresh;

    static boolean granted(Context c) {
        String s = Settings.Secure.getString(c.getContentResolver(), "enabled_notification_listeners");
        return s != null && s.contains(c.getPackageName());
    }

    @Override public void onListenerConnected() { schedule(); }
    @Override public void onNotificationPosted(StatusBarNotification sbn) { schedule(); }
    @Override public void onNotificationRemoved(StatusBarNotification sbn) { schedule(); }

    private void schedule() {
        h.removeCallbacks(job);
        h.postDelayed(job, 1500);
    }

    private void refresh() {
        try {
            StatusBarNotification[] list = getActiveNotifications();
            PackageManager pm = getPackageManager();
            JSONArray items = new JSONArray();
            int n = 0;
            if (list != null) {
                for (StatusBarNotification s : list) {
                    Notification nt = s.getNotification();
                    if (s.isOngoing() || (nt.flags & Notification.FLAG_GROUP_SUMMARY) != 0) continue;
                    if (getPackageName().equals(s.getPackageName())) continue;
                    n++;
                    if (items.length() < 5) {
                        String app;
                        try { app = pm.getApplicationLabel(pm.getApplicationInfo(s.getPackageName(), 0)).toString(); }
                        catch (Exception e) { app = s.getPackageName(); }
                        CharSequence t = nt.extras.getCharSequence(Notification.EXTRA_TITLE);
                        items.put(t == null || t.length() == 0 ? app : app + ": " + t);
                    }
                }
            }
            JSONObject o = new JSONObject();
            o.put("n", n);
            o.put("items", items);
            Store.putString(this, "notif", o.toString());
            Updater.async(this, Updater.TICK, null);
        } catch (Exception ignored) {}
    }
}
