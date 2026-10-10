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
import android.provider.AlarmClock;
import android.util.TypedValue;
import android.view.View;
import android.widget.RemoteViews;
import org.json.JSONObject;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;

/**
 * Политика обновления:
 *  - часы:   системный TextClock, сам каждую минуту (секунд нет);
 *  - дата/день недели: раз в сутки — на первом «тике» с включённым экраном после смены дня;
 *  - погода: не чаще раза в 6/12/24 часа, только при включённом экране; прогноз приходит тем же запросом;
 *  - календарь: перечитывается на «тике» (раз в 5 минут, экран включён); страницы по 2 строки листает ViewFlipper;
 *  - при выключенном экране ничего не рисуется и устройство не будится (не-wakeup будильник).
 * Все виджеты обновляются «на месте»: никаких новых экземпляров не создаётся.
 */
final class Updater {
    private Updater() {}

    static final int TICK = 0, SYSTEM = 1, FORCE = 2;
    static final String ACT_TICK = "__APP_ID__.TICK";
    static final String ACT_REFRESH = "__APP_ID__.REFRESH";
    private static final long TICK_MS = 5 * 60_000L;
    private static final Object LOCK = new Object();

    static final String[] FAMILIES = {"sans-serif", "sans-serif-light", "sans-serif-thin", "sans-serif-medium", "sans-serif-black", "sans-serif-condensed", "serif", "monospace", "casual", "cursive", "ws_lora", "ws_lora_bold", "ws_plex_serif", "ws_plex_serif_bold", "ws_poiret", "ws_jura_light", "ws_jura", "ws_tektur", "ws_jetbrains", "ws_geist_mono"};
    /** Первые SYSTEM_COUNT — системные шрифты; остальные — встроенные (res/font), доступны виджету с Android 8. */
    static final int SYSTEM_COUNT = 10;
    /** Запасной системный шрифт для встроенных на Android 7.x. */
    static final int[] FALLBACK = {0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 6, 6, 6, 6, 2, 1, 3, 3, 7, 7};
    private static final int[] CLK = {R.id.clock_f0, R.id.clock_f1, R.id.clock_f2, R.id.clock_f3, R.id.clock_f4, R.id.clock_f5, R.id.clock_f6, R.id.clock_f7, R.id.clock_f8, R.id.clock_f9, R.id.clock_f10, R.id.clock_f11, R.id.clock_f12, R.id.clock_f13, R.id.clock_f14, R.id.clock_f15, R.id.clock_f16, R.id.clock_f17, R.id.clock_f18, R.id.clock_f19};

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
        new Thread(new Runnable() {
            @Override public void run() {
                try { updateAll(app, mode); } catch (Throwable ignored) {}
                finally { if (pr != null) pr.finish(); }
            }
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

    /**
     * Размеры: [0],[1] — размер картинки (может быть уменьшен по лимиту памяти RemoteViews),
     * [2],[3] — реальный размер виджета в пикселях (по нему ставятся часы и зоны нажатия).
     * densOut[0] — плотность для картинки, densOut[1] — реальная плотность.
     */
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
        int rw = Math.round(w), rh = Math.round(h);
        float bd = dens;
        float limit = 1_200_000f; // лимит памяти RemoteViews (картинка + страницы календаря)
        if (w * h > limit) { float k = (float) Math.sqrt(limit / (w * h)); w *= k; h *= k; bd *= k; }
        densOut[0] = bd;
        densOut[1] = dens;
        return new int[]{Math.round(w), Math.round(h), rw, rh};
    }

    private static void updateOne(Context c, AppWidgetManager m, int id, int mode) throws Exception {
        long now = System.currentTimeMillis();
        JSONObject cfg = Store.cfg(c);
        float[] dens = new float[2];
        int[] sz = sizePx(c, m, id, dens);

        // дата — раз в сутки
        long dts = Store.getLong(c, "dateTs_" + id, 0);
        if (mode == FORCE || !sameDay(dts, now)) { dts = now; Store.putLong(c, "dateTs_" + id, dts); }

        // погода — по интервалу (не меньше 6 ч)
        JSONObject wc = Renderer.sub(cfg, "weather");
        StringBuilder sig = new StringBuilder();
        if (wc.optBoolean("show", true) && wc.has("lat")) {
            long interval = Math.max(6, wc.optInt("hours", 6)) * 3600_000L;
            WeatherService.Data wd = WeatherService.ensure(c, wc, false, interval);
            sig.append(wd == null ? "x" : wd.ts + ":" + Math.round(wd.temp));
        }

        // календарь
        JSONObject cal = Renderer.sub(cfg, "calendar");
        List<CalendarService.Ev> evs = cal.optBoolean("show", true)
                ? CalendarService.list(c, cal.optInt("days", 2), cal.optJSONArray("ids")) : new ArrayList<CalendarService.Ev>();
        sig.append("|ev:").append(CalendarService.signature(evs));

        sig.append('|').append(cfg.toString().hashCode()).append('|').append(dts / 86400000L).append('|').append(sz[0]).append('x').append(sz[1]);
        sig.append('|').append(Store.getLong(c, "bgRev", 0));
        sig.append('|').append(java.util.TimeZone.getDefault().getID());
        String sg = sig.toString();
        if (mode != FORCE && sg.equals(Store.getString(c, "sig_" + id, ""))) return;

        Bitmap bmp = Renderer.render(c, cfg, sz[0], sz[1], dens[0], dts, false, false);
        List<Bitmap> strips = new ArrayList<>();
        RectF area = Renderer.calRect(cfg, sz[0], sz[1]);
        for (List<CalendarService.Ev> page : CalendarService.pages(evs)) strips.add(Renderer.eventStrip(cfg, page, area, dens[0]));
        m.updateAppWidget(id, build(c, id, cfg, bmp, strips, sz[2], sz[3]));
        Store.putString(c, "sig_" + id, sg);
    }

    private static PendingIntent activityPi(Context c, int code, Intent i) {
        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        return PendingIntent.getActivity(c, code, i, PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
    }

    /** Если подходящего приложения нет (часы/календарь) — открываем наше приложение. */
    private static Intent orApp(Context c, Intent wanted) {
        try {
            if (wanted.resolveActivity(c.getPackageManager()) != null) return wanted;
        } catch (Exception ignored) {}
        Intent launch = c.getPackageManager().getLaunchIntentForPackage(c.getPackageName());
        return launch != null ? launch : wanted;
    }

    private static RemoteViews build(Context c, int id, JSONObject cfg, Bitmap bmp, List<Bitmap> strips, int rw, int rh) {
        Renderer.init(c);
        RemoteViews rv = new RemoteViews(c.getPackageName(), R.layout.widget_root);
        rv.setImageViewBitmap(R.id.bg_image, bmp);

        // тап по виджету — открыть приложение
        Intent launch = c.getPackageManager().getLaunchIntentForPackage(c.getPackageName());
        if (launch != null) rv.setOnClickPendingIntent(R.id.widget_root, activityPi(c, id * 10, launch));

        // часы (TextClock) + тап «открыть часы»
        JSONObject ck = Renderer.sub(cfg, "clock");
        RectF r = Renderer.clockRect(cfg, rw, rh);
        if (!ck.optBoolean("show", true)) {
            rv.setViewVisibility(R.id.clock_host, View.GONE);
            rv.setViewVisibility(R.id.tap_clock_host, View.GONE);
        } else {
            String fmt = ck.optString("fmt", "HH:mm"), tz = ck.optString("tz", "");
            int fam = 1;
            for (int i = 0; i < FAMILIES.length; i++) if (FAMILIES[i].equals(ck.optString("font", "sans-serif-light"))) fam = i;
            if (fam >= SYSTEM_COUNT && android.os.Build.VERSION.SDK_INT < 26) fam = FALLBACK[fam];
            int fg = Renderer.col(cfg.optString("fg", "#FFFFFF"), -1, 0xFFFFFFFF);
            String cs = ck.optString("color", "");
            int color = cs.isEmpty() ? fg : Renderer.col(cs, -1, fg);
            rv.setViewVisibility(R.id.clock_host, View.VISIBLE);
            // справа отступ 0: текст прижат влево и сам укладывается в блок
            rv.setViewPadding(R.id.clock_host, Math.round(r.left), Math.round(r.top), 0, Math.round(rh - r.bottom));
            for (int i = 0; i < CLK.length; i++) rv.setViewVisibility(CLK[i], i == fam ? View.VISIBLE : View.GONE);
            int cid = CLK[fam];
            rv.setCharSequence(cid, "setFormat12Hour", fmt);
            rv.setCharSequence(cid, "setFormat24Hour", fmt);
            rv.setString(cid, "setTimeZone", tz.isEmpty() ? java.util.TimeZone.getDefault().getID() : tz);
            rv.setTextColor(cid, color);
            rv.setTextViewTextSize(cid, TypedValue.COMPLEX_UNIT_PX, Renderer.clockSize(cfg, rw, rh));

            rv.setViewVisibility(R.id.tap_clock_host, View.VISIBLE);
            rv.setViewPadding(R.id.tap_clock_host, Math.round(r.left), Math.round(r.top), Math.round(rw - r.right), Math.round(rh - r.bottom));
            rv.setOnClickPendingIntent(R.id.tap_clock, activityPi(c, id * 10 + 1, orApp(c, new Intent(AlarmClock.ACTION_SHOW_ALARMS))));
        }

        // события календаря: скрыто, если событий нет; 3+ события листаются по кругу
        RectF cr = Renderer.calRect(cfg, rw, rh);
        if (strips.isEmpty()) {
            rv.setViewVisibility(R.id.cal_host, View.GONE);
        } else {
            rv.setViewVisibility(R.id.cal_host, View.VISIBLE);
            rv.setViewPadding(R.id.cal_host, Math.round(cr.left), Math.round(cr.top), Math.round(rw - cr.right), Math.round(rh - cr.bottom));
            rv.removeAllViews(R.id.cal_flipper);
            for (Bitmap b : strips) {
                RemoteViews page = new RemoteViews(c.getPackageName(), R.layout.widget_page);
                page.setImageViewBitmap(R.id.page_img, b);
                rv.addView(R.id.cal_flipper, page);
            }
            Intent cal = new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_APP_CALENDAR);
            rv.setOnClickPendingIntent(R.id.tap_cal, activityPi(c, id * 10 + 2, orApp(c, cal)));
        }

        // кнопка обновления (круглая, в правом верхнем углу)
        boolean show = cfg.optBoolean("refresh", true);
        rv.setViewVisibility(R.id.btn_refresh, show ? View.VISIBLE : View.GONE);
        if (show) {
            rv.setViewPadding(R.id.btn_host, 0, Math.round(0.06f * rh), Math.round(0.04f * rw), 0);
            Intent ri = new Intent(c, TickReceiver.class).setAction(ACT_REFRESH);
            rv.setOnClickPendingIntent(R.id.btn_refresh, PendingIntent.getBroadcast(c, 1, ri,
                    PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT));
        }
        return rv;
    }
}
