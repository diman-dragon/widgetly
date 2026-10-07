package __APP_ID__;

import android.app.AlarmManager;
import android.content.Context;
import android.graphics.*;
import android.text.format.DateFormat;
import org.json.JSONArray;
import org.json.JSONObject;
import java.text.SimpleDateFormat;
import java.util.*;

/**
 * Рисует виджет в Bitmap по JSON-описанию дизайна. Один и тот же код даёт и предпросмотр в редакторе,
 * и картинку для виджета на рабочем столе. Часы на самом виджете — системный TextClock (drawClock=false).
 */
final class Renderer {
    private Renderer() {}

    // ---------- helpers ----------
    static int col(String hex, int alphaPct, int def) {
        try {
            int c = Color.parseColor(hex);
            return alphaPct >= 0 ? (c & 0x00FFFFFF) | ((Math.round(alphaPct * 2.55f)) << 24) : c;
        } catch (Exception e) { return def; }
    }

    static Typeface tf(String fam) {
        return Typeface.create(fam == null || fam.isEmpty() ? "sans-serif" : fam, Typeface.NORMAL);
    }

    static RectF blockRect(JSONObject d, JSONObject b, int w, int h, float dens) {
        int cols = Math.max(1, d.optInt("cols", 5)), rows = Math.max(1, d.optInt("rows", 3));
        float pad = (float) d.optDouble("pad", 8) * dens;
        float cw = (w - 2 * pad) / cols, ch = (h - 2 * pad) / rows;
        int x = b.optInt("x"), y = b.optInt("y"), bw = Math.max(1, b.optInt("w", 1)), bh = Math.max(1, b.optInt("h", 1));
        return new RectF(pad + x * cw, pad + y * ch, pad + (x + bw) * cw, pad + (y + bh) * ch);
    }

    static RectF contentRect(RectF r, float dens) {
        RectF c = new RectF(r);
        c.inset(5 * dens, 5 * dens);
        return c;
    }

    static float fitSize(Paint p, String text, float w, float h, float scale) {
        p.setTextSize(100);
        float tw = Math.max(1, p.measureText(text));
        Paint.FontMetrics fm = p.getFontMetrics();
        float th = Math.max(1, fm.descent - fm.ascent);
        return 100 * Math.min(w / tw, h / th) * scale;
    }

    static void drawFit(Canvas cv, String text, Paint p, RectF r, String align, float scale) {
        if (text == null || text.isEmpty() || r.width() <= 0 || r.height() <= 0) return;
        p.setTextSize(fitSize(p, text, r.width(), r.height(), scale));
        Paint.FontMetrics fm = p.getFontMetrics();
        float tw = p.measureText(text);
        float x = "center".equals(align) ? r.centerX() - tw / 2 : "right".equals(align) ? r.right - tw : r.left;
        cv.drawText(text, x, r.centerY() - (fm.ascent + fm.descent) / 2, p);
    }

    static final class Ln {
        String t; float wgt, a = 1f, sc = 1f;
        Ln(String t, float w) { this.t = t; this.wgt = w; }
        Ln a(float v) { a = v; return this; }
    }

    static void stack(Canvas cv, Paint p, RectF r, List<Ln> ls, String al, float scale) {
        float tot = 0;
        for (Ln l : ls) tot += l.wgt;
        if (tot <= 0) return;
        int base = p.getColor();
        float y = r.top;
        for (Ln l : ls) {
            float hh = r.height() * l.wgt / tot;
            RectF band = new RectF(r.left, y, r.right, y + hh);
            band.inset(0, hh * 0.04f);
            p.setColor((base & 0x00FFFFFF) | ((int) (Color.alpha(base) * l.a) << 24));
            drawFit(cv, l.t, p, band, al, scale * l.sc);
            y += hh;
        }
        p.setColor(base);
    }

    static float alignShift(String al, float boxW, float textW) {
        return "center".equals(al) ? (boxW - textW) / 2 : "right".equals(al) ? boxW - textW : 0;
    }

    // ---------- clock geometry (общая для превью и TextClock) ----------
    static String clockSample(String fmt) { return fmt.contains("a") ? "00:00 PM" : "00:00"; }

    /** [textSizePx, shiftPx] */
    static float[] clockGeom(JSONObject b, RectF cr) {
        JSONObject o = b.optJSONObject("opt");
        String fmt = o == null ? "HH:mm" : o.optString("fmt", "HH:mm");
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        p.setTypeface(tf(b.optString("font", "sans-serif")));
        String sample = clockSample(fmt);
        float size = fitSize(p, sample, cr.width(), cr.height(), (float) b.optDouble("scale", 1));
        p.setTextSize(size);
        return new float[]{size, alignShift(b.optString("align", "left"), cr.width(), p.measureText(sample))};
    }

    // ---------- main ----------
    static Bitmap render(Context c, JSONObject d, int w, int h, float dens, long dateTs, boolean drawClock, boolean net) {
        Bitmap bmp = Bitmap.createBitmap(Math.max(1, w), Math.max(1, h), Bitmap.Config.ARGB_8888);
        Canvas cv = new Canvas(bmp);
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        float rad = (float) d.optDouble("radius", 24) * dens;
        int c1 = col(d.optString("bg", "#15161A"), d.optInt("bgA", 85), 0xD915161A);
        String b2 = d.optString("bg2", "");
        if (!b2.isEmpty()) {
            p.setShader(new LinearGradient(0, 0, w * 0.35f, h, c1, col(b2, d.optInt("bgA", 85), c1), Shader.TileMode.CLAMP));
        } else {
            p.setColor(c1);
        }
        cv.drawRoundRect(new RectF(0, 0, w, h), rad, rad, p);
        JSONArray bl = d.optJSONArray("blocks");
        if (bl == null) bl = new JSONArray();
        int fg = col(d.optString("fg", "#FFFFFF"), -1, Color.WHITE);
        for (int i = 0; i < bl.length(); i++) {
            JSONObject b = bl.optJSONObject(i);
            if (b == null) continue;
            try { drawBlock(c, cv, d, b, w, h, dens, fg, dateTs, drawClock, net); } catch (Exception ignored) {}
        }
        drawDividers(cv, d, bl, w, h, dens);
        return bmp;
    }

    static Bitmap message(String msg, int w, int h, float dens) {
        Bitmap bmp = Bitmap.createBitmap(Math.max(1, w), Math.max(1, h), Bitmap.Config.ARGB_8888);
        Canvas cv = new Canvas(bmp);
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        p.setColor(0xD915161A);
        cv.drawRoundRect(new RectF(0, 0, w, h), 24 * dens, 24 * dens, p);
        p.setColor(Color.WHITE);
        RectF r = new RectF(12 * dens, 12 * dens, w - 12 * dens, h - 12 * dens);
        drawFit(cv, msg, p, r, "center", 0.5f);
        return bmp;
    }

    private static void drawBlock(Context c, Canvas cv, JSONObject d, JSONObject b, int w, int h, float dens,
                                  int fg, long dateTs, boolean drawClock, boolean net) throws Exception {
        RectF r = blockRect(d, b, w, h, dens);
        String bg = b.optString("bg", "");
        if (!bg.isEmpty()) {
            Paint q = new Paint(Paint.ANTI_ALIAS_FLAG);
            q.setColor(col(bg, b.optInt("bgA", 30), 0));
            RectF in = new RectF(r);
            in.inset(2 * dens, 2 * dens);
            float rr = (float) b.optDouble("radius", 12) * dens;
            cv.drawRoundRect(in, rr, rr, q);
        }
        RectF cr = contentRect(r, dens);
        String cs = b.optString("color", "");
        int color = cs.isEmpty() ? fg : col(cs, -1, fg);
        JSONObject o = b.optJSONObject("opt");
        if (o == null) o = new JSONObject();
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        p.setColor(color);
        p.setTypeface(tf(b.optString("font", "sans-serif")));
        float sc = (float) b.optDouble("scale", 1);
        String al = b.optString("align", "left");
        String type = b.optString("type");

        switch (type) {
            case "clock": {
                if (!drawClock) break;
                SimpleDateFormat f = new SimpleDateFormat(o.optString("fmt", "HH:mm"), Locale.getDefault());
                String tz = o.optString("tz", "");
                if (!tz.isEmpty()) f.setTimeZone(TimeZone.getTimeZone(tz));
                float[] g = clockGeom(b, cr);
                p.setTextSize(g[0]);
                Paint.FontMetrics fm = p.getFontMetrics();
                cv.drawText(f.format(new Date()), cr.left + g[1], cr.centerY() - (fm.ascent + fm.descent) / 2, p);
                break;
            }
            case "date": {
                String s;
                try { s = new SimpleDateFormat(o.optString("fmt", "EEEE, d MMMM"), Locale.getDefault()).format(new Date(dateTs)); }
                catch (Exception e) { s = new SimpleDateFormat("EEEE, d MMMM", Locale.getDefault()).format(new Date(dateTs)); }
                if (o.optBoolean("upper")) s = s.toUpperCase(Locale.getDefault());
                else if (!s.isEmpty()) s = s.substring(0, 1).toUpperCase(Locale.getDefault()) + s.substring(1);
                drawFit(cv, s, p, cr, al, sc);
                break;
            }
            case "weather": drawWeather(c, cv, p, cr, o, al, sc, net); break;
            case "notif": drawNotif(c, cv, p, cr, o, al, sc, dens); break;
            case "alarm": drawFit(cv, "⏰ " + alarmText(c), p, cr, al, sc); break;
            default: break;
        }
    }

    // ---------- weather ----------
    private static void drawWeather(Context c, Canvas cv, Paint p, RectF cr, JSONObject o, String al, float sc, boolean net) {
        WeatherService.Data wd = WeatherService.get(c, o, net);
        String temp = wd == null ? "--°" : Math.round(wd.temp) + "°";
        String icon = wd == null ? "☁️" : WeatherService.emoji(wd.code, wd.day);
        String cond = wd == null ? "" : WeatherService.text(wd.code);
        String hilo = wd == null ? "" : "↑" + Math.round(wd.hi) + "° ↓" + Math.round(wd.lo) + "°";
        String city = o.optString("city", "");
        boolean horiz = cr.width() >= cr.height() * 1.5f;
        boolean showIcon = o.optBoolean("icon", true);
        RectF textR = new RectF(cr);
        List<Ln> ls = new ArrayList<>();
        if (horiz) {
            if (showIcon) {
                float side = Math.min(cr.height(), cr.width() * 0.4f);
                RectF ir = new RectF(cr.left, cr.centerY() - side / 2, cr.left + side, cr.centerY() + side / 2);
                Paint ip = new Paint(p);
                drawFit(cv, icon, ip, ir, "center", 0.8f);
                textR.left = ir.right + side * 0.1f;
            }
            ls.add(new Ln(temp, 2.2f));
        } else {
            if (showIcon) ls.add(new Ln(icon, 2f));
            ls.add(new Ln(temp, 2f));
        }
        if (o.optBoolean("showCity", true) && !city.isEmpty()) ls.add(new Ln(city, 0.9f).a(0.85f));
        if (o.optBoolean("showCond", true) && !cond.isEmpty()) ls.add(new Ln(cond, 0.8f).a(0.7f));
        if (o.optBoolean("showHiLo", false) && !hilo.isEmpty()) ls.add(new Ln(hilo, 0.8f).a(0.7f));
        stack(cv, p, textR, ls, al, sc);
    }

    // ---------- notifications / alarm ----------
    private static void drawNotif(Context c, Canvas cv, Paint p, RectF cr, JSONObject o, String al, float sc, float dens) throws Exception {
        List<Ln> ls = new ArrayList<>();
        if (!NotifListener.granted(c)) {
            ls.add(new Ln("🔔 —", 1.2f));
            ls.add(new Ln(WeatherService.ru() ? "Нет доступа" : "No access", 0.7f).a(0.6f));
        } else {
            JSONObject n = new JSONObject(Store.getString(c, "notif", "{}"));
            ls.add(new Ln("🔔 " + n.optInt("n", 0), 1.3f));
            JSONArray items = n.optJSONArray("items");
            if (o.optBoolean("list", true) && items != null) {
                int fit = Math.max(0, (int) (cr.height() / (20 * dens)) - 1);
                int max = Math.min(Math.min(o.optInt("max", 3), fit), items.length());
                for (int i = 0; i < max; i++) ls.add(new Ln(items.getString(i), 0.8f).a(0.75f));
            }
        }
        stack(cv, p, cr, ls, al, sc);
    }

    static String alarmText(Context c) {
        try {
            AlarmManager am = (AlarmManager) c.getSystemService(Context.ALARM_SERVICE);
            AlarmManager.AlarmClockInfo ai = am.getNextAlarmClock();
            if (ai == null) return "—";
            long t = ai.getTriggerTime();
            boolean h24 = DateFormat.is24HourFormat(c);
            String pat = (t - System.currentTimeMillis() > 20 * 3600_000L ? "EEE " : "") + (h24 ? "HH:mm" : "h:mm a");
            return new SimpleDateFormat(pat, Locale.getDefault()).format(new Date(t));
        } catch (Exception e) { return "—"; }
    }

    // ---------- dividers ----------
    private static void drawDividers(Canvas cv, JSONObject d, JSONArray bl, int w, int h, float dens) {
        JSONObject v = d.optJSONObject("div");
        if (v == null || !v.optBoolean("on")) return;
        int cols = Math.max(1, d.optInt("cols", 5)), rows = Math.max(1, d.optInt("rows", 3));
        float wd = Math.max(1f, (float) v.optDouble("wd", 1) * dens);
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        p.setStyle(Paint.Style.STROKE);
        p.setColor(col(v.optString("color", "#FFFFFF"), v.optInt("a", 25), 0x40FFFFFF));
        p.setStrokeWidth(wd);
        String st = v.optString("style", "solid");
        if ("dashed".equals(st)) p.setPathEffect(new DashPathEffect(new float[]{6 * dens, 4 * dens}, 0));
        else if ("dotted".equals(st)) { p.setStrokeCap(Paint.Cap.ROUND); p.setPathEffect(new DashPathEffect(new float[]{0.01f, wd * 2.5f}, 0)); }
        float ins = (float) v.optDouble("inset", 10) * dens;
        for (int i = 0; i < bl.length(); i++) {
            JSONObject b = bl.optJSONObject(i);
            if (b == null) continue;
            RectF r = blockRect(d, b, w, h, dens);
            Path path = new Path();
            if (b.optBoolean("divR") && b.optInt("x") + b.optInt("w", 1) < cols) {
                path.moveTo(r.right, r.top + ins); path.lineTo(r.right, r.bottom - ins);
            }
            if (b.optBoolean("divB") && b.optInt("y") + b.optInt("h", 1) < rows) {
                path.moveTo(r.left + ins, r.bottom); path.lineTo(r.right - ins, r.bottom);
            }
            cv.drawPath(path, p);
        }
    }
}
