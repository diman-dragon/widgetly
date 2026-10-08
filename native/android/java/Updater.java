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
import android.view.View;
import android.widget.RemoteViews;
import org.json.JSONObject;
import java.util.Calendar;

/**
 * Политика обновления:
 *  - часы:   системный TextClock, сам каждую минуту (секунд нет);
 *  - дата/день недели: раз в сутки — на первом «тике» с включённым экраном после смены дня;
 *  - погода: не чаще раза в 6/12/24 часа (по настройке), только при включённом экране; прогноз приходит тем же запросом;
 *  - при выключенном экране ничего не рисуется и устройство не будится (не-wakeup будильник).
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

    static int[] allIds(Context c, AppWidgetManager m) {
        return m.getAppWidgetIds(new ComponentName(c, WidgetProvider.class));
    }

    static boolean screenOn(Context c) {
        PowerManager pm = (PowerManager) c.getSystemService(Context.POWER_SERVICE);
        return pm == null || pm.isInteractive();
    }

    // ---------- расписание: не-wakeup, неточный, раз в 5 минут ----------
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

    // ---------- запуск ----------
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

    static int[] sizePx(Context c, AppWidgetManager m, int id, float[] densOut) {
        Bundle o = m.getAppWidgetOptions(id);
        float dens = c.getResources().getDisplayMetrics().density;
        boolean land = c.getResources().getConfiguration().orientation == Configuration.ORIENTATION_LANDSCAPE;
        int wdp = o.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH, 0);
        int hdp = o.getInt(AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT, 0);
        if (land) {
            wdp = o.getInt(AppWidgetManager.OPTION_APPWIDGET_MAX_WIDTH, 0);
            hdp = o.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT, 0);
        }
        if (wdp <= 0 || hdp <= 0) { wdp = 320; hdp = 180; }
        float w = wdp * dens, h = hdp * dens;
        float limit = 1_400_000f; // лимит памяти RemoteViews
        if (w * h > limit) { float k = (float) Math.sqrt(limit / (w * h)); w *= k; h *= k; dens *= k; }
        densOut[0] = dens;
        return new int[]{Math.round(w), Math.round(h)};
    }

    private static void updateOne(Context c, AppWidgetManager m, int id, int mode) throws Exception {
        long now = System.currentTimeMillis();
        JSONObject cfg = Store.cfg(c);
        float[] dens = new float[1];
        int[] wh = sizePx(c, m, id, dens);

        // дата — раз в сутки
        long dts = Store.getLong(c, "dateTs_" + id, 0);
        if (mode == FORCE || !sameDay(dts, now)) { dts = now; Store.putLong(c, "dateTs_" + id, dts); }

        // погода — по интервалу (не меньше 6 ч), только при включённом экране (тик/система/кнопка — всегда с экрана)
        JSONObject wc = Renderer.sub(cfg, "weather");
        StringBuilder sig = new StringBuilder();
        if (wc.optBoolean("show", true) && wc.has("lat")) {
            long interval = Math.max(6, wc.optInt("hours", 6)) * 3600_000L;
            WeatherService.Data wd = WeatherService.ensure(c, wc, false, interval);
            sig.append(wd == null ? "x" : wd.ts + ":" + Math.round(wd.temp));
        }
        sig.append('|').append(cfg.toString().hashCode()).append('|').append(dts / 86400000L).append('|').append(wh[0]).append('x').append(wh[1]);
        sig.append('|').append(Store.getLong(c, "bgRev", 0));
        sig.append('|').append(java.util.TimeZone.getDefault().getID()); // смена пояса → перерисовка
        String sg = sig.toString();
        if (mode != FORCE && sg.equals(Store.getString(c, "sig_" + id, ""))) return;

        Bitmap bmp = Renderer.render(c, cfg, wh[0], wh[1], dens[0], dts, false, false);
        m.updateAppWidget(id, build(c, id, cfg, bmp, wh[0], wh[1]));
        Store.putString(c, "sig_" + id, sg);
    }

    private static RemoteViews build(Context c, int id, JSONObject cfg, Bitmap bmp, int w, int h) {
        RemoteViews rv = new RemoteViews(c.getPackageName(), R.layout.widget_root);
        rv.setImageViewBitmap(R.id.bg_image, bmp);

        // тап по виджету — открыть приложение
        Intent launch = c.getPackageManager().getLaunchIntentForPackage(c.getPackageName());
        if (launch != null) {
            rv.setOnClickPendingIntent(R.id.widget_root, PendingIntent.getActivity(c, id, launch,
                    PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT));
        }

        // часы (TextClock)
        JSONObject ck = Renderer.sub(cfg, "clock");
        if (!ck.optBoolean("show", true)) {
            rv.setViewVisibility(R.id.clock_host, View.GONE);
        } else {
            RectF r = Renderer.clockRect(cfg, w, h);
            String fmt = ck.optString("fmt", "HH:mm"), tz = ck.optString("tz", "");
            int fam = 1;
            for (int i = 0; i < FAMILIES.length; i++) if (FAMILIES[i].equals(ck.optString("font", "sans-serif-light"))) fam = i;
            int fg = Renderer.col(cfg.optString("fg", "#FFFFFF"), -1, 0xFFFFFFFF);
            String cs = ck.optString("color", "");
            int color = cs.isEmpty() ? fg : Renderer.col(cs, -1, fg);
            rv.setViewVisibility(R.id.clock_host, View.VISIBLE);
            // справа отступ 0: текст прижат влево и сам укладывается в блок, лишней ширины хватает, чтобы ничего не обрезалось
            rv.setViewPadding(R.id.clock_host, Math.round(r.left), Math.round(r.top), 0, Math.round(h - r.bottom));
            for (int i = 0; i < CLK.length; i++) rv.setViewVisibility(CLK[i], i == fam ? View.VISIBLE : View.GONE);
            int cid = CLK[fam];
            rv.setCharSequence(cid, "setFormat12Hour", fmt);
            rv.setCharSequence(cid, "setFormat24Hour", fmt);
            // часовой пояс задаём всегда: свой или текущий пояс устройства
            rv.setString(cid, "setTimeZone", tz.isEmpty() ? java.util.TimeZone.getDefault().getID() : tz);
            rv.setTextColor(cid, color);
            rv.setTextViewTextSize(cid, TypedValue.COMPLEX_UNIT_PX, Renderer.clockSize(cfg, w, h));
        }

        // кнопка обновления (круглая, в правом верхнем углу)
        boolean show = cfg.optBoolean("refresh", true);
        rv.setViewVisibility(R.id.btn_refresh, show ? View.VISIBLE : View.GONE);
        if (show) {
            rv.setViewPadding(R.id.btn_host, 0, Math.round(0.06f * h), Math.round(0.04f * w), 0);
            Intent ri = new Intent(c, TickReceiver.class).setAction(ACT_REFRESH);
            rv.setOnClickPendingIntent(R.id.btn_refresh, PendingIntent.getBroadcast(c, 1, ri,
                    PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT));
        }
        return rv;
    }
}
