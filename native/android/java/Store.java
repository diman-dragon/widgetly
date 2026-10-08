package __APP_ID__;

import android.content.Context;
import android.content.SharedPreferences;
import org.json.JSONObject;
import java.io.File;

/** SharedPreferences "ws": конфиг виджета (JSON), настройки приложения, кэш погоды, служебные метки. */
final class Store {
    private Store() {}

    static SharedPreferences sp(Context c) {
        return c.getApplicationContext().getSharedPreferences("ws", Context.MODE_PRIVATE);
    }

    static String cfgJson(Context c) { return sp(c).getString("cfg", "{}"); }
    static void saveCfg(Context c, String j) { sp(c).edit().putString("cfg", j).apply(); }
    static String settingsJson(Context c) { return sp(c).getString("settings", "{}"); }
    static void saveSettings(Context c, String j) { sp(c).edit().putString("settings", j).apply(); }

    static JSONObject cfg(Context c) {
        try { return new JSONObject(cfgJson(c)); } catch (Exception e) { return new JSONObject(); }
    }

    static long getLong(Context c, String k, long d) { return sp(c).getLong(k, d); }
    static void putLong(Context c, String k, long v) { sp(c).edit().putLong(k, v).apply(); }
    static String getString(Context c, String k, String d) { return sp(c).getString(k, d); }
    static void putString(Context c, String k, String v) { sp(c).edit().putString(k, v).apply(); }

    static void forget(Context c, int widgetId) {
        sp(c).edit().remove("dateTs_" + widgetId).remove("sig_" + widgetId).apply();
    }

    static File bgFile(Context c) { return new File(c.getFilesDir(), "bg.jpg"); }
}
