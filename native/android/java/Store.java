package __APP_ID__;

import android.content.Context;
import android.content.SharedPreferences;
import org.json.JSONArray;
import org.json.JSONObject;

/** Всё хранится в SharedPreferences "ws": дизайны (JSON), настройки, привязка виджет→дизайн, кэши. */
final class Store {
    private Store() {}

    static SharedPreferences sp(Context c) {
        return c.getApplicationContext().getSharedPreferences("ws", Context.MODE_PRIVATE);
    }

    static String designsJson(Context c) { return sp(c).getString("designs", "[]"); }
    static void saveDesigns(Context c, String j) { sp(c).edit().putString("designs", j).apply(); }
    static String settingsJson(Context c) { return sp(c).getString("settings", "{}"); }
    static void saveSettings(Context c, String j) { sp(c).edit().putString("settings", j).apply(); }

    static JSONObject settings(Context c) {
        try { return new JSONObject(settingsJson(c)); } catch (Exception e) { return new JSONObject(); }
    }

    static long getLong(Context c, String k, long d) { return sp(c).getLong(k, d); }
    static void putLong(Context c, String k, long v) { sp(c).edit().putLong(k, v).apply(); }
    static String getString(Context c, String k, String d) { return sp(c).getString(k, d); }
    static void putString(Context c, String k, String v) { sp(c).edit().putString(k, v).apply(); }

    static String designIdFor(Context c, int widgetId) {
        String id = sp(c).getString("map_" + widgetId, "");
        return id.isEmpty() ? null : id;
    }

    static void mapWidget(Context c, int widgetId, String designId) {
        sp(c).edit().putString("map_" + widgetId, designId).remove("sig_" + widgetId).apply();
    }

    static void forget(Context c, int widgetId) {
        sp(c).edit().remove("map_" + widgetId).remove("dateTs_" + widgetId).remove("sig_" + widgetId).apply();
    }

    /** Дизайн для конкретного виджета; новому виджету назначается "ожидающий" (после pin) или первый. */
    static JSONObject designFor(Context c, int widgetId) {
        try {
            JSONArray a = new JSONArray(designsJson(c));
            String id = sp(c).getString("map_" + widgetId, "");
            if (id.isEmpty()) {
                String pend = sp(c).getString("pending", "");
                long ts = sp(c).getLong("pendingTs", 0);
                if (!pend.isEmpty() && System.currentTimeMillis() - ts < 120000) {
                    id = pend;
                    sp(c).edit().remove("pending").apply();
                } else if (a.length() > 0) {
                    id = a.getJSONObject(0).optString("id");
                }
                if (!id.isEmpty()) mapWidget(c, widgetId, id);
            }
            for (int i = 0; i < a.length(); i++) {
                JSONObject d = a.getJSONObject(i);
                if (id.equals(d.optString("id"))) return d;
            }
            return a.length() > 0 ? a.getJSONObject(0) : null;
        } catch (Exception e) {
            return null;
        }
    }
}
