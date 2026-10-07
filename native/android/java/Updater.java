package __APP_ID__;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.appwidget.AppWidgetManager;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.res.Configuration;
import android.graphics.Bitmap;
import android.graphics.RectF;
import android.os.Bundle;
import android.os.PowerManager;
import android.util.TypedValue;
import android.widget.RemoteViews;
import org.json.JSONArray;
import org.json.JSONObject;
import java.util.Calendar;

/**
 * Политика обновления:
 *  - часы:   системный TextClock, сам каждую минуту (секунд нет);
 *  - дата:   один раз в сутки — при первом "тике" с включённым экраном после смены дня;
 *  - погода: раз в N часов (по умолчанию 6) — только при включённом экране;
 *  - уведомления/будильник: по событию / на тике, только при включённом экране;
 *  - при выключенном экране ничего не делаем и не будим устройство (будильник не-wakeup).
 */
final class Updater {
    private Updater() {}

    static final int TICK = 0, SYSTEM = 1, FORCE = 2;
    static final String ACT_TICK = "__APP_ID__.TICK";
    static final String ACT_REFRESH = "__APP_ID__.REFRESH";
    private static final long TICK_MS = 5 * 60_000L;
    private static final Object LOCK = new Object();

    static final String[] FAMILIES = {"sans-serif", "sans-serif-light", "sans-serif-thin", "sans-serif-medium",
            "sans-serif-black", "sans-serif-condensed", "serif", "monospace", "casual", "cursive"};
    private static final int[] CLK = {R.id.clock_f0, R.id.clock_f1, R.id.clock_f2, R.id.clock_f3, R.id.clock_f4,
            R.id.clock_f5, R.id.clock_f6, R.id.clock_f7, R.id.clock_f8, R.id.clock_f9};
    private static final int[] BTN = {R.id.btn_tl, R.id.btn_tr, R.id.btn_bl, R.id.btn_br};
    private static final String[] BTN_KEY = {"tl", "tr", "bl", "br"};

    static final Class<?>[] PROVIDERS = {Widget5x3.class, Widget4x2.class, Widget2x2.class};

    static int[] allIds(Context c, AppWidgetManager m) {
        int n = 0;
        int[][] parts = new int[PROVIDERS.length][];
        for (int i = 0; i < PROVIDERS.length; i++) {
            parts[i] = m.getAppWidgetIds(new ComponentName(c, PROVIDERS[i]));
            n += parts[i].length;
        }
        int[] out = new int[n];
        int k = 0;
        for (int[] a : parts) for (int v : a) out[k++] = v;
        return out;
    }

    static boolean screenOn(Context c) {
        PowerManager pm = (PowerManager) c.getSystemService(Context.POWER_SERVICE);
        return pm == null || pm.isInteractive();
    }

    // ---------- scheduling: не-wakeup, неточный, раз в 5 минут ----------
    private static PendingIntent tickPi(Context c) {
        Intent i = new Intent(c, TickReceiver.class).setAction(ACT_TICK);
        return PendingIntent.getBroadcast(c, 0, i, PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
    }

    static void schedule(Context c) {
        AlarmManager am = (AlarmManager) c.getSystemService(Context.ALARM_SERVICE);
        am.setInexactRepeating(AlarmManager.RTC, System.currentTimeMillis() + TICK_MS, TICK_MS, tickPi(c));
    }

    static void cancel(Context c) {
        ((AlarmManager) c.getSystemService(Context.ALARM_SERVICE)).cancel(tickPi(c));
    }

    static void checkSchedule(Context c) {
        if (allIds(c, AppWidgetManager.getInstance(c)).length == 0) cancel(c); else schedule(c);
    }

    // ---------- run ----------
    static void async(final Context c, final int mode, final BroadcastReceiver.PendingResult pr) {
        final Context app = c.getApplicationContext();
        new Thread(() -> {
            try { updateAll(app, mode); } catch (Throwable ignored) {}
            finally { if (pr != null) pr.finish(); }
        }).start();
    }

    static void updateAll(Context c, int mode) {
        c = c.getApplicationContext();
        synchronized (LOCK) {
            AppWidgetManager m = AppWidgetManager.getInstance(c);
            int[] ids = allIds(c, m);
            if (ids.length == 0) { cancel(c); return; }
            schedule(c);
            if (mode == TICK && !screenOn(c)) return;
            for (int id : ids) {
                try { updateOne(c, m, id, mode); } catch (Throwable ignored) {}
            }
        }
    }

    private static boolean sameDay(long a, long b) {
        if (a == 0) return false;
        Calendar x = Calendar.getInstance(), y = Calendar.getInstance();
        x.setTimeInMillis(a); y.setTimeInMillis(b);
        return x.get(Calendar.YEAR) == y.get(Calendar.YEAR) && x.get(Calendar.DAY_OF_YEAR) == y.get(Calendar.DAY_OF_YEAR);
    }

    static int[] sizePx(Context c, AppWidgetManager m, int id, JSONObject d, float[] densOut) {
        Bundle o = m.getAppWidgetOptions(id);
        float dens = c.getResources().getDisplayMetrics().density;
        boolean land = c.getResources().getConfiguration().orientation == Configuration.ORIENTATION_LANDSCAPE;
        int wdp = o.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH, 0);
        int hdp = o.getInt(AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT, 0);
        if (land) {
            wdp = o.getInt(AppWidgetManager.OPTION_APPWIDGET_MAX_WIDTH, 0);
            hdp = o.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT, 0);
        }
        if (wdp <= 0 || hdp <= 0) { wdp = d.optInt("cols", 5) * 72; hdp = d.optInt("rows", 3) * 84; }
        float w = wdp * dens, h = hdp * dens;
        float limit = 1_400_000f; // лимит памяти RemoteViews
        if (w * h > limit) { float k = (float) Math.sqrt(limit / (w * h)); w *= k; h *= k; dens *= k; }
        densOut[0] = dens;
        return new int[]{Math.round(w), Math.round(h)};
    }

    private static void updateOne(Context c, AppWidgetManager m, int id, int mode) throws Exception {
        long now = System.currentTimeMillis();
        JSONObject d = Store.designFor(c, id);
        float[] dens = new float[1];
        if (d == null) {
            int[] wh = sizePx(c, m, id, new JSONObject(), dens);
            Bitmap bmp = Renderer.message(WeatherService.ru() ? "Откройте Widget Studio" : "Open Widget Studio", wh[0], wh[1], dens[0]);
            RemoteViews rv = new RemoteViews(c.getPackageName(), R.layout.widget_root);
            rv.setImageViewBitmap(R.id.bg_image, bmp);
            m.updateAppWidget(id, rv);
            return;
        }
        // дата — раз в сутки
        long dts = Store.getLong(c, "dateTs_" + id, 0);
        if (mode == FORCE || !sameDay(dts, now)) { dts = now; Store.putLong(c, "dateTs_" + id, dts); }

        // погода — по интервалу
        long interval = Store.settings(c).optInt("weatherHours", 6) * 3600_000L;
        StringBuilder sig = new StringBuilder();
        JSONArray bl = d.optJSONArray("blocks");
        if (bl == null) bl = new JSONArray();
        for (int i = 0; i < bl.length(); i++) {
            JSONObject b = bl.optJSONObject(i);
            if (b == null) continue;
            String type = b.optString("type");
            JSONObject o = b.optJSONObject("opt");
            if ("weather".equals(type) && o != null) {
                WeatherService.Data wd = WeatherService.ensure(c, o, mode == FORCE, interval);
                sig.append(wd == null ? "x" : wd.ts + ":" + Math.round(wd.temp)).append(';');
            } else if ("alarm".equals(type)) {
                sig.append(Renderer.alarmText(c)).append(';');
            } else if ("notif".equals(type)) {
                sig.append(Store.getString(c, "notif", "")).append(NotifListener.granted(c)).append(';');
            }
        }
        int[] wh = sizePx(c, m, id, d, dens);
        sig.append(d.toString().hashCode()).append('|').append(Long.toString(dts / 86400000L)).append('|').append(wh[0]).append('x').append(wh[1]);
        String sg = sig.toString();
        if (mode != FORCE && sg.equals(Store.getString(c, "sig_" + id, ""))) return;

        Bitmap bmp = Renderer.render(c, d, wh[0], wh[1], dens[0], dts, false, false);
        m.updateAppWidget(id, build(c, id, d, bmp, wh[0], wh[1], dens[0]));
        Store.putString(c, "sig_" + id, sg);
    }

    private static RemoteViews build(Context c, int id, JSONObject d, Bitmap bmp, int w, int h, float dens) {
        RemoteViews rv = new RemoteViews(c.getPackageName(), R.layout.widget_root);
        rv.setImageViewBitmap(R.id.bg_image, bmp);

        // клик по виджету — открыть редактор этого дизайна
        Intent launch = c.getPackageManager().getLaunchIntentForPackage(c.getPackageName());
        if (launch != null) {
            launch.putExtra("edit", d.optString("id"));
            rv.setOnClickPendingIntent(R.id.widget_root, PendingIntent.getActivity(c, id, launch,
                    PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT));
        }

        // часы (TextClock)
        JSONObject clock = null;
        JSONArray bl = d.optJSONArray("blocks");
        for (int i = 0; bl != null && i < bl.length(); i++) {
            JSONObject b = bl.optJSONObject(i);
            if (b != null && "clock".equals(b.optString("type"))) { clock = b; break; }
        }
        if (clock == null) {
            rv.setViewVisibility(R.id.clock_host, android.view.View.GONE);
        } else {
            RectF cr = Renderer.contentRect(Renderer.blockRect(d, clock, w, h, dens), dens);
            float[] g = Renderer.clockGeom(clock, cr);
            JSONObject o = clock.optJSONObject("opt");
            String fmt = o == null ? "HH:mm" : o.optString("fmt", "HH:mm");
            String tz = o == null ? "" : o.optString("tz", "");
            int fam = 0;
            for (int i = 0; i < FAMILIES.length; i++) if (FAMILIES[i].equals(clock.optString("font", "sans-serif"))) fam = i;
            int fg = Renderer.col(d.optString("fg", "#FFFFFF"), -1, 0xFFFFFFFF);
            String cs = clock.optString("color", "");
            int color = cs.isEmpty() ? fg : Renderer.col(cs, -1, fg);
            rv.setViewVisibility(R.id.clock_host, android.view.View.VISIBLE);
            rv.setViewPadding(R.id.clock_host, Math.round(cr.left), Math.round(cr.top), Math.round(w - cr.right), Math.round(h - cr.bottom));
            for (int i = 0; i < CLK.length; i++) rv.setViewVisibility(CLK[i], i == fam ? android.view.View.VISIBLE : android.view.View.GONE);
            int cid = CLK[fam];
            rv.setCharSequence(cid, "setFormat12Hour", fmt);
            rv.setCharSequence(cid, "setFormat24Hour", fmt);
            if (!tz.isEmpty()) rv.setString(cid, "setTimeZone", tz);
            rv.setTextColor(cid, color);
            rv.setTextViewTextSize(cid, TypedValue.COMPLEX_UNIT_PX, g[0]);
            rv.setViewPadding(cid, Math.round(g[1]), 0, 0, 0);
        }

        // кнопка обновления
        boolean show = d.optBoolean("refresh", true);
        String corner = d.optString("rc", "tr");
        Intent ri = new Intent(c, TickReceiver.class).setAction(ACT_REFRESH);
        PendingIntent rpi = PendingIntent.getBroadcast(c, 1, ri, PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
        int fg2 = Renderer.col(d.optString("fg", "#FFFFFF"), 80, 0xCCFFFFFF);
        for (int i = 0; i < BTN.length; i++) {
            boolean on = show && BTN_KEY[i].equals(corner);
            rv.setViewVisibility(BTN[i], on ? android.view.View.VISIBLE : android.view.View.GONE);
            if (on) {
                rv.setOnClickPendingIntent(BTN[i], rpi);
                rv.setInt(BTN[i], "setColorFilter", fg2);
            }
        }
        return rv;
    }
}
